package com.taisau.android.common.download.download

import com.taisau.android.common.download.ChunkInfo
import com.taisau.android.common.download.DownloadConfig
import com.taisau.android.common.download.DownloadStatus
import com.taisau.android.common.download.db.ChunkEntity
import com.taisau.android.common.download.db.DownloadDao
import com.taisau.android.common.download.engine.DownloadEngine
import com.taisau.android.common.download.utils.DownloadLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 大文件分片下载协调器 —— 将文件拆分为多个 [DownloadTask]（各写临时文件），最终合并。
 *
 * ### 流程
 * 1. 初始化/恢复分片（从 [ChunkEntity] 表恢复）
 * 2. 为每个分片创建 [DownloadTask]，下载到 `fileName.part.{N}` 临时文件
 * 3. 合并临时文件 → 最终文件
 * 4. 清理临时文件和 ChunkEntity 记录
 */
internal class ChunkedDownloader(
    private val request: DownloadRequest,
    private val fileSize: Long,
    private val config: DownloadConfig,
    private val dao: DownloadDao,
    private val engine: DownloadEngine = config.engine
) : IDownloader {

    override val downloadId: String = request.calculateDownloadId()

    private val logger = config.logger
    private val chunkedConfig = config.chunkedConfig
    private val targetFile = File(request.filePath, request.fileName)
    private val isCancelled = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private val chunks = ConcurrentHashMap<Int, ChunkInfo>()
    private val totalDownloadedBytes = AtomicLong(0)
    private val lastEmitTime = AtomicLong(0)
    private val lastEmitBytes = AtomicLong(0)

    // 保存 execute() 传入的参数，供 resume() 使用
    private var savedEtag: String? = null

    private fun partFileName(index: Int) = "${request.fileName}.part.$index"
    private fun partFile(index: Int) = File(request.filePath, partFileName(index))

    override suspend fun execute(
        existingEtag: String?,
        existingLastModified: String?
    ): Flow<DownloadStatus> = callbackFlow {
        savedEtag = existingEtag
        val job = launch(Dispatchers.IO) {
            try {
                performChunkedDownload(existingEtag) { status -> trySend(status) }
            } catch (e: CancellationException) {
                when {
                    isPaused.get() -> trySend(DownloadStatus.Paused(downloadId))
                    isCancelled.get() -> trySend(DownloadStatus.Cancelled(downloadId))
                }
            } catch (e: Exception) {
                logger.log(DownloadLogger.LogPriority.ERROR, "ChunkedDownload", "分片下载失败", e)
                trySend(DownloadStatus.Error(downloadId, e))
            }
        }
        awaitClose { job.cancel() }
    }

    private suspend fun performChunkedDownload(
        existingEtag: String?,
        onStatus: (DownloadStatus) -> Unit
    ) {
        targetFile.parentFile?.mkdirs()

        // ETag 校验
        if (targetFile.exists() && existingEtag != null) {
            val serverEtag = getEtagFromServer()
            if (serverEtag != null && serverEtag != existingEtag) {
                logger.log(
                    DownloadLogger.LogPriority.INFO,
                    "ChunkedDownload",
                    "服务端文件已变更（$existingEtag → $serverEtag），重新下载"
                )
                targetFile.delete()
                cleanupPartFiles()
                dao.deleteChunksByTaskId(downloadId)
            }
        }

        initializeChunks()

        onStatus(DownloadStatus.Progress(
            downloadId = downloadId,
            url = request.url,
            downloadedBytes = totalDownloadedBytes.get(),
            totalBytes = fileSize,
            speed = 0
        ))

        // 并行下载各分片
        val semaphore = Semaphore(chunkedConfig.maxParallelChunks)
        coroutineScope {
            chunks.values
                .filter { !it.isCompleted.get() && !it.isFailed.get() }
                .map { chunkInfo ->
                    async {
                        semaphore.acquire()
                        try {
                            downloadChunk(chunkInfo, onStatus)
                        } finally {
                            semaphore.release()
                        }
                    }
                }
                .awaitAll()
        }

        // 检查是否全部完成
        val allCompleted = chunks.values.all { it.isCompleted.get() }
        if (allCompleted) {
            mergePartFiles()
            dao.deleteChunksByTaskId(downloadId)
            cleanupPartFiles()

            if (targetFile.exists() && targetFile.length() == fileSize) {
                onStatus(DownloadStatus.Success(downloadId, targetFile.absolutePath))
            } else {
                throw IOException("文件完整性校验失败: 期望 ${fileSize}B, 实际 ${targetFile.length()}B")
            }
        }
    }

    private suspend fun downloadChunk(
        chunkInfo: ChunkInfo,
        onStatus: (DownloadStatus) -> Unit
    ) {
        var retryCount = 0
        val maxRetries = config.maxRetries

        while (retryCount < maxRetries && !chunkInfo.isCompleted.get() && !isCancelled.get()) {
            if (isPaused.get()) {
                delay(100)
                continue
            }

            try {
                val actualStart = chunkInfo.startByte + chunkInfo.downloadedBytes.get()
                if (actualStart > chunkInfo.endByte) {
                    chunkInfo.isCompleted.set(true)
                    return
                }

                val pf = partFile(chunkInfo.index)
                pf.parentFile?.mkdirs()

                // 委托 DownloadTask
                val task = DownloadTask(
                    request = request,
                    config = config,
                    outputFile = pf,
                    startByte = actualStart,
                    endByte = chunkInfo.endByte,
                    resumeBytes = chunkInfo.downloadedBytes.get()
                )

                task.execute().collect { status ->
                    if (isCancelled.get()) throw CancellationException("分片下载已取消")
                    if (isPaused.get()) {
                        saveChunkProgress()
                        throw CancellationException("分片下载已暂停")
                    }

                    when (status) {
                        is DownloadStatus.Progress -> {
                            val taskProgress = status.downloadedBytes - actualStart
                            chunkInfo.downloadedBytes.addAndGet(taskProgress)
                            totalDownloadedBytes.addAndGet(taskProgress)
                            emitOverallProgress(onStatus)
                        }
                        is DownloadStatus.Success -> {
                            chunkInfo.isCompleted.set(true)
                            val remaining = chunkInfo.totalBytes - chunkInfo.downloadedBytes.get()
                            if (remaining > 0) {
                                chunkInfo.downloadedBytes.set(chunkInfo.totalBytes)
                                totalDownloadedBytes.addAndGet(remaining)
                            }
                            emitOverallProgress(onStatus)
                        }
                        is DownloadStatus.Error -> throw status.error
                        else -> {}
                    }
                }

                chunkInfo.isCompleted.set(true)
                return

            } catch (e: CancellationException) {
                saveChunkProgress()
                throw e
            } catch (e: Exception) {
                retryCount++
                chunkInfo.retryCount.incrementAndGet()
                logger.log(
                    DownloadLogger.LogPriority.WARN,
                    "ChunkedDownload",
                    "分片 ${chunkInfo.index} 第 $retryCount 次重试: ${e.message}"
                )
                if (retryCount >= maxRetries) {
                    chunkInfo.isFailed.set(true)
                    saveChunkProgress()
                    throw e
                }
                delay(1000L * retryCount)
            }
        }
    }

    private suspend fun emitOverallProgress(onStatus: (DownloadStatus) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastEmitTime.get() >= PROGRESS_INTERVAL_MS) {
            val total = totalDownloadedBytes.get()
            val speed = if (lastEmitTime.get() > 0) {
                val dt = now - lastEmitTime.get()
                val db = total - lastEmitBytes.get()
                if (dt > 0) (db * 1000) / dt else 0L
            } else 0L

            lastEmitTime.set(now)
            lastEmitBytes.set(total)

            onStatus(DownloadStatus.Progress(
                downloadId = downloadId,
                url = request.url,
                downloadedBytes = total,
                totalBytes = fileSize,
                speed = speed,
                percent = if (fileSize > 0) {
                    (total.toFloat() / fileSize * 100).coerceIn(0f, 100f)
                } else 0f
            ))
            saveChunkProgress()
        }
    }

    private suspend fun mergePartFiles() = withContext(Dispatchers.IO) {
        logger.log(DownloadLogger.LogPriority.INFO, "ChunkedDownload", "合并 ${chunks.size} 个分片文件")

        RandomAccessFile(targetFile, "rw").use { out ->
            for (i in 0 until chunks.size) {
                val pf = partFile(i)
                if (!pf.exists()) throw IOException("分片文件丢失: ${pf.name}")
                pf.inputStream().use { ins ->
                    val buf = ByteArray(MERGE_BUFFER_SIZE)
                    var bytesRead: Int
                    while (ins.read(buf).also { bytesRead = it } != -1) {
                        out.write(buf, 0, bytesRead)
                    }
                }
            }
        }

        logger.log(DownloadLogger.LogPriority.INFO, "ChunkedDownload", "合并完成: ${targetFile.absolutePath}")
    }

    private fun cleanupPartFiles() {
        for (i in 0 until chunks.size) {
            partFile(i).delete()
        }
    }

    private suspend fun initializeChunks() {
        val savedChunks = dao.getChunksByTaskId(downloadId)
        if (savedChunks.isNotEmpty()) {
            logger.log(DownloadLogger.LogPriority.INFO, "ChunkedDownload", "从数据库恢复 ${savedChunks.size} 个分片")
            savedChunks.forEach { entity ->
                val pf = partFile(entity.chunkIndex)
                val expectedSize = entity.endByte - entity.startByte + 1
                val isPartComplete = entity.isCompleted && pf.exists() && pf.length() == expectedSize

                chunks[entity.chunkIndex] = ChunkInfo(
                    index = entity.chunkIndex,
                    startByte = entity.startByte,
                    endByte = entity.endByte,
                    downloadedBytes = AtomicLong(if (isPartComplete) expectedSize else entity.downloadedBytes),
                    isCompleted = AtomicBoolean(isPartComplete),
                    isFailed = AtomicBoolean(entity.isFailed)
                )
                totalDownloadedBytes.addAndGet(if (isPartComplete) expectedSize else entity.downloadedBytes)
            }
            return
        }

        val chunkSize = chunkedConfig.chunkSize
        var startByte = 0L
        var index = 0
        while (startByte < fileSize) {
            val endByte = minOf(startByte + chunkSize - 1, fileSize - 1)
            chunks[index] = ChunkInfo(index = index, startByte = startByte, endByte = endByte)
            startByte = endByte + 1
            index++
        }

        logger.log(DownloadLogger.LogPriority.INFO, "ChunkedDownload", "创建 $index 个分片, 分片大小: ${chunkSize / 1024}KB")
    }

    private suspend fun saveChunkProgress() {
        dao.insertOrUpdateChunks(chunks.values.map { info ->
            ChunkEntity(
                taskId = downloadId,
                chunkIndex = info.index,
                startByte = info.startByte,
                endByte = info.endByte,
                downloadedBytes = info.downloadedBytes.get(),
                isCompleted = info.isCompleted.get(),
                isFailed = info.isFailed.get()
            )
        })
    }

    private suspend fun getEtagFromServer(): String? {
        return try { engine.execute(request).headers["ETag"] } catch (_: Exception) { null }
    }

    override suspend fun resume(): Flow<DownloadStatus> {
        isPaused.set(false)
        isCancelled.set(false)
        return execute(savedEtag, null)
    }

    override fun pause() { isPaused.set(true) }

    override fun cancel() {
        isCancelled.set(true)
        isPaused.set(false)
    }

    companion object {
        private const val PROGRESS_INTERVAL_MS = 200L
        private const val MERGE_BUFFER_SIZE = 65536
    }
}
