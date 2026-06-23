package com.taisau.android.common.download.download

import com.taisau.android.common.download.DownloadConfig
import com.taisau.android.common.download.DownloadStatus
import com.taisau.android.common.download.engine.DownloadEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * 范围下载任务 —— 从 [startByte] 下载到 [endByte]，写入 [outputFile]。
 *
 * ### 设计原则
 * 只做一件事：HTTP Range 请求 → Okio 写入文件。
 * 不做：ETag 校验、重试、断点续传上下文管理（这些由 [SingleDownloader] 或 [ChunkedDownloader] 负责）。
 *
 * 响应体读取策略（按 [DownloadResponse.body] 实际类型自动选择）：
 * 1. [BufferedSource] — Okio 原生路径（[OkHttpEngine][com.taisau.android.common.download.engine.OkHttpEngine] 默认）
 * 2. [InputStream] — 标准 Java IO 路径（自定义引擎可返回）
 *
 * @property request       下载请求（URL、headers 等）
 * @property config        下载配置
 * @property outputFile    写入的目标文件
 * @property startByte     HTTP Range 起始字节
 * @property endByte       HTTP Range 结束字节（-1 表示到文件末尾）
 * @property resumeBytes   outputFile 中已有的字节数（断点续传时跳过这部分写入）
 */
internal class DownloadTask(
    private val request: DownloadRequest,
    private val config: DownloadConfig,
    private val outputFile: File,
    private val startByte: Long = 0L,
    private val endByte: Long = -1L,
    private val resumeBytes: Long = 0L,
    private val engine: DownloadEngine = config.engine
) {
    val downloadId: String = request.calculateDownloadId()

    /**
     * 执行下载，返回状态 Flow。
     *
     * 进度中的 [DownloadStatus.Progress.downloadedBytes] 和
     * [DownloadStatus.Progress.totalBytes] 是**相对于整个文件**的绝对值
     *（即 [startByte] + 当前分片已下载量）。
     */
    suspend fun execute(): Flow<DownloadStatus> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            try {
                performDownload { status -> trySend(status) }
            } catch (_: CancellationException) {
                // 取消/暂停由调用方处理
            } catch (e: Exception) {
                trySend(DownloadStatus.Error(downloadId, e))
            }
        }
        awaitClose { job.cancel() }
    }

    private suspend fun performDownload(onStatus: (DownloadStatus) -> Unit) = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()

        // 构建 Range 请求头
        val headers = request.headers.toMutableMap()
        val rangeEndSpec = if (endByte >= 0) endByte.toString() else ""
        if (startByte > 0 || endByte >= 0) {
            headers["Range"] = "bytes=$startByte-$rangeEndSpec"
        }
        val rangeRequest = request.copy(headers = headers)

        // 执行 HTTP 请求
        val response = engine.execute(rangeRequest)
        if (response.responseCode != 200 && response.responseCode != 206) {
            throw IOException("服务器返回 ${response.responseCode}")
        }

        // 计算本次任务的总字节数
        val rangeTotal = when {
            endByte >= 0 -> endByte - startByte + 1
            response.responseCode == 206 -> {
                val cr = response.headers["Content-Range"]
                cr?.substringAfter("/")?.toLongOrNull()?.let { it - startByte }
                    ?: response.contentLength
            }
            else -> response.contentLength
        }
        val taskTotal = resumeBytes + rangeTotal

        // 发射初始进度
        onStatus(DownloadStatus.Progress(
            downloadId = downloadId,
            url = request.url,
            downloadedBytes = startByte + resumeBytes,
            totalBytes = startByte + taskTotal,
            speed = 0
        ))

        // 使用 Okio 写入文件
        val body = response.body ?: throw IOException("响应体为空")
        val path = outputFile.absolutePath.toPath()

        val sink = if (resumeBytes > 0) {
            FileSystem.SYSTEM.appendingSink(path)
        } else {
            FileSystem.SYSTEM.sink(path)
        }

        sink.buffer().use { bufferedSink ->
            val buffer = ByteArray(BUFFER_SIZE)
            var writtenThisSession = 0L
            var lastEmitTime = System.currentTimeMillis()

            val bytesRead = when (body) {
                is BufferedSource -> readFromBufferedSource(body, buffer, bufferedSink) { written ->
                    writtenThisSession = written
                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime >= PROGRESS_INTERVAL_MS) {
                        emitProgress(onStatus, writtenThisSession, taskTotal, now)
                        lastEmitTime = now
                    }
                    if (!isActive) throw CancellationException("下载已取消")
                }
                is InputStream -> readFromInputStream(body, buffer, bufferedSink) { written ->
                    writtenThisSession = written
                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime >= PROGRESS_INTERVAL_MS) {
                        emitProgress(onStatus, writtenThisSession, taskTotal, now)
                        lastEmitTime = now
                    }
                    if (!isActive) throw CancellationException("下载已取消")
                }
                else -> throw IOException("不支持的响应体类型: ${body.javaClass.name}")
            }

            if (!isActive) throw CancellationException("下载已取消")

            // 发射最终进度
            emitProgress(onStatus, bytesRead, taskTotal, System.currentTimeMillis())
            bufferedSink.flush()
        }

        // 完成
        onStatus(DownloadStatus.Success(
            downloadId = downloadId,
            filePath = outputFile.absolutePath
        ))
    }

    private fun readFromBufferedSource(
        source: BufferedSource,
        buffer: ByteArray,
        sink: okio.BufferedSink,
        onBatch: (Long) -> Unit
    ): Long {
        var totalRead = 0L
        var bytesRead: Int

        while (source.read(buffer).also { bytesRead = it } != -1) {
            sink.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            onBatch(totalRead)
        }

        return totalRead
    }

    private fun readFromInputStream(
        stream: InputStream,
        buffer: ByteArray,
        sink: okio.BufferedSink,
        onBatch: (Long) -> Unit
    ): Long {
        var totalRead = 0L
        var bytesRead: Int

        while (stream.read(buffer).also { bytesRead = it } != -1) {
            sink.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            onBatch(totalRead)
        }

        return totalRead
    }

    private fun emitProgress(
        onStatus: (DownloadStatus) -> Unit,
        writtenThisSession: Long,
        taskTotal: Long,
        now: Long
    ) {
        val speed = if (taskTotal > 0) {
            val elapsed = System.currentTimeMillis() - (now - writtenThisSession.coerceAtMost(1000))
            if (elapsed > 0) (writtenThisSession * 1000) / elapsed else 0L
        } else 0L

        onStatus(DownloadStatus.Progress(
            downloadId = downloadId,
            url = request.url,
            downloadedBytes = startByte + resumeBytes + writtenThisSession,
            totalBytes = startByte + taskTotal,
            speed = speed,
            percent = if (taskTotal > 0) {
                ((resumeBytes + writtenThisSession).toFloat() / taskTotal * 100)
                    .coerceIn(0f, 100f)
            } else 0f
        ))
    }

    companion object {
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_INTERVAL_MS = 200L
    }
}
