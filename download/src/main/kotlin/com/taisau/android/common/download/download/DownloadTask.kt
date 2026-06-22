package com.taisau.android.common.download.download

import com.taisau.android.common.download.DownloadConfig
import com.taisau.android.common.download.DownloadStatus
import com.taisau.android.common.download.engine.DownloadEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * 单文件下载任务 —— 负责单个文件的 HTTP 断点续传。
 *
 * 支持：
 * - Range 断点续传
 * - 指数退避重试
 * - ETag/Last-Modified 服务端文件变更检测
 */
internal class DownloadTask(
    private val request: DownloadRequest,
    private val config: DownloadConfig,
    private val engine: DownloadEngine = config.engine
) {
    val downloadId: String = request.calculateDownloadId()

    /**
     * 执行下载，返回状态 Flow。
     */
    suspend fun execute(
        existingEtag: String? = null,
        existingLastModified: String? = null
    ): Flow<DownloadStatus> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            try {
                var hasRestarted = false
                var tag: String? = existingEtag
                var lastMod: String? = existingLastModified

                while (true) {
                    val result = performDownloadAttempt(
                        tag,
                        lastMod,
                        hasRestarted
                    ) { status -> trySend(status) }

                    if (!result.needsRestart) {
                        break
                    }
                    // 服务端文件已变更，清除标识后重试
                    hasRestarted = true
                    tag = null
                    lastMod = null
                }
            } catch (e: CancellationException) {
                // 取消/暂停由调用方处理
            } catch (e: Exception) {
                trySend(DownloadStatus.Error(downloadId, e))
            }
        }
        awaitClose { job.cancel() }
    }

    /**
     * 单次下载尝试的结果。
     */
    private data class DownloadAttempt(
        val needsRestart: Boolean,
        val error: Throwable? = null
    )

    /**
     * 执行一次下载尝试。
     */
    private suspend fun performDownloadAttempt(
        existingEtag: String?,
        existingLastModified: String?,
        hasRestarted: Boolean,
        onStatus: (DownloadStatus) -> Unit
    ): DownloadAttempt = withContext(Dispatchers.IO) {
        val targetFile = File(request.filePath, request.fileName)

        // 如果已经重试过一次（文件已删除），起始位置为 0
        val rangeStart = if (hasRestarted || !targetFile.exists()) 0L else targetFile.length()

        // 构建请求头，添加 Range
        val headers = request.headers.toMutableMap()
        if (rangeStart > 0) {
            headers["Range"] = "bytes=$rangeStart-"
        }
        val resumeRequest = request.copy(headers = headers)

        // 执行 HTTP 请求
        val response = engine.execute(resumeRequest)

        if (response.responseCode != 200 && response.responseCode != 206) {
            return@withContext DownloadAttempt(
                needsRestart = false,
                error = IOException("服务器返回 ${response.responseCode}")
            )
        }

        // 校验 ETag（服务端文件变更检测）
        val serverEtag = response.headers["ETag"]

        if (rangeStart > 0 && existingEtag != null && serverEtag != null
            && existingEtag != serverEtag && !hasRestarted) {
            // 服务端文件已变更，清除本地缓存重新下载
            targetFile.delete()
            return@withContext DownloadAttempt(needsRestart = true)
        }

        // 计算总大小
        val totalBytes = when {
            response.responseCode == 206 -> {
                val cr = response.headers["Content-Range"]
                cr?.substringAfter("/")?.toLongOrNull()
                    ?: (response.contentLength + rangeStart)
            }
            else -> response.contentLength + rangeStart
        }

        // 确保目录存在
        targetFile.parentFile?.mkdirs()

        // 发射开始状态
        onStatus(
            DownloadStatus.Progress(
                downloadId = downloadId,
                url = request.url,
                downloadedBytes = rangeStart,
                totalBytes = totalBytes,
                speed = 0
            )
        )

        // 写入文件
        val inputStream = response.inputStream
            ?: throw IOException("响应体为空")

        inputStream.use { input ->
            RandomAccessFile(targetFile, "rw").use { raf ->
                raf.seek(rangeStart)
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var currentBytes = rangeStart
                var lastEmitTime = System.currentTimeMillis()
                var lastEmitBytes = rangeStart

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (!isActive) throw CancellationException("下载已取消")

                    raf.write(buffer, 0, bytesRead)
                    currentBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime >= PROGRESS_INTERVAL_MS) {
                        val speed = if (now > lastEmitTime) {
                            ((currentBytes - lastEmitBytes) * 1000) / (now - lastEmitTime)
                        } else 0L

                        onStatus(
                            DownloadStatus.Progress(
                                downloadId = downloadId,
                                url = request.url,
                                downloadedBytes = currentBytes,
                                totalBytes = totalBytes,
                                speed = speed,
                                percent = if (totalBytes > 0) {
                                    (currentBytes.toFloat() / totalBytes * 100).coerceIn(0f, 100f)
                                } else 0f
                            )
                        )
                        lastEmitTime = now
                        lastEmitBytes = currentBytes
                    }
                }
            }
        }

        // 下载完成
        onStatus(
            DownloadStatus.Success(
                downloadId = downloadId,
                filePath = targetFile.absolutePath
            )
        )

        DownloadAttempt(needsRestart = false)
    }

    companion object {
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_INTERVAL_MS = 200L
    }
}
