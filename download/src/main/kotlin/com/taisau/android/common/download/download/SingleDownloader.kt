package com.taisau.android.common.download.download

import com.taisau.android.common.download.DownloadConfig
import com.taisau.android.common.download.DownloadStatus
import com.taisau.android.common.download.engine.DownloadEngine
import com.taisau.android.common.download.utils.DownloadLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * 单文件下载执行器 —— 处理断点续传、ETag 校验、重试，内部使用 [DownloadTask] 执行实际范围下载。
 *
 * 职责：
 * 1. 检测本地缓存文件，计算续传起始位置
 * 2. ETag 校验（服务端文件变更时抛弃缓存）
 * 3. 指数退避重试
 * 4. 委托 [DownloadTask] 执行纯范围下载
 */
internal class SingleDownloader(
    private val request: DownloadRequest,
    private val config: DownloadConfig,
    private val engine: DownloadEngine = config.engine
) : IDownloader {

    override val downloadId: String = request.calculateDownloadId()
    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)

    // 保存 execute() 传入的参数，供 resume() 使用
    private var savedEtag: String? = null
    private var savedLastModified: String? = null

    override suspend fun execute(
        existingEtag: String?,
        existingLastModified: String?
    ): Flow<DownloadStatus> = callbackFlow {
        savedEtag = existingEtag
        savedLastModified = existingLastModified
        val job = launch(Dispatchers.IO) {
            try {
                performWithRetry(existingEtag, existingLastModified) { status ->
                    trySend(status)
                }
            } catch (_: CancellationException) {
                when {
                    isPaused.get() -> trySend(DownloadStatus.Paused(downloadId))
                    isCancelled.get() -> trySend(DownloadStatus.Cancelled(downloadId))
                }
            } catch (e: Exception) {
                trySend(DownloadStatus.Error(downloadId, e))
            }
        }
        awaitClose { job.cancel() }
    }

    private suspend fun performWithRetry(
        existingEtag: String?,
        existingLastModified: String?,
        onStatus: (DownloadStatus) -> Unit
    ) {
        val targetFile = File(request.filePath, request.fileName)
        val maxRetries = config.maxRetries
        var retryCount = 0

        while (retryCount <= maxRetries && currentCoroutineContext().isActive) {
            if (isPaused.get()) {
                delay(100)
                continue
            }
            if (isCancelled.get()) throw CancellationException("下载已取消")

            try {
                val hasRestarted = retryCount > 0
                performSingleAttempt(targetFile, existingEtag, hasRestarted, onStatus)
                return  // 成功
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                retryCount++
                if (retryCount > maxRetries) {
                    config.logger.log(
                        DownloadLogger.LogPriority.ERROR,
                        "SingleDownloader",
                        "下载失败（已重试 $maxRetries 次）: ${request.url}",
                        e
                    )
                    throw e
                }
                config.logger.log(
                    DownloadLogger.LogPriority.WARN,
                    "SingleDownloader",
                    "下载失败，第 $retryCount 次重试: ${e.message}"
                )
                delay((1000L * retryCount).milliseconds)
            }
        }
    }

    private suspend fun performSingleAttempt(
        targetFile: File,
        existingEtag: String?,
        hasRestarted: Boolean,
        onStatus: (DownloadStatus) -> Unit
    ) {
        val rangeStart = if (hasRestarted || !targetFile.exists()) 0L else targetFile.length()

        // ETag 校验
        if (rangeStart > 0 && existingEtag != null) {
            val serverEtag = getEtagFromServer()
            if (serverEtag != null && serverEtag != existingEtag) {
                config.logger.log(
                    DownloadLogger.LogPriority.INFO,
                    "SingleDownloader",
                    "服务端文件已变更（$existingEtag → $serverEtag），重新下载"
                )
                targetFile.delete()
                performSingleAttempt(targetFile, null, true, onStatus)
                return
            }
        }

        // 委托 DownloadTask 执行实际范围下载
        val task = DownloadTask(
            request = request,
            config = config,
            outputFile = targetFile,
            startByte = rangeStart,
            endByte = -1L,
            resumeBytes = rangeStart
        )

        task.execute().collect { status ->
            if (status is DownloadStatus.Success) {
                onStatus(DownloadStatus.Success(downloadId, targetFile.absolutePath))
            } else {
                onStatus(status)
            }
        }
    }

    private suspend fun getEtagFromServer(): String? {
        return try {
            engine.execute(request).headers["ETag"]
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun resume(): Flow<DownloadStatus> {
        isPaused.set(false)
        isCancelled.set(false)
        return execute(savedEtag, savedLastModified)
    }

    override fun pause() { isPaused.set(true) }

    override fun cancel() {
        isCancelled.set(true)
        isPaused.set(false)
    }
}
