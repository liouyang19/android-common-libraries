package com.taisau.android.common.download.download

import com.taisau.android.common.download.DownloadConfig
import com.taisau.android.common.download.DownloadInfo
import com.taisau.android.common.download.DownloadManager
import com.taisau.android.common.download.DownloadPriority
import com.taisau.android.common.download.DownloadStatus
import com.taisau.android.common.download.Disposable
import com.taisau.android.common.download.TaskStatus
import com.taisau.android.common.download.db.DownloadDao
import com.taisau.android.common.download.db.toEntity
import com.taisau.android.common.download.engine.DownloadEngine
import com.taisau.android.common.download.utils.DownloadLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [DownloadManager] 的默认实现 —— 基于 Channel + Semaphore + PriorityQueue 架构。
 *
 * ### 核心设计（源自 Gemini 建议）
 * - [enqueue]：普通函数，通过 [Channel] 实现多线程安全并行入队
 * - [startDispatcherLoop]：常驻协程循环，消费 Channel 中的请求
 * - [Semaphore]：控制最大并发下载数
 * - [PriorityQueue]：支持高优先级任务插队
 * - [preCheckDiskSpace]：下载前预检磁盘空间，防止中途崩溃
 *
 * ### 调度流程
 * ```
 * enqueue(request) ──→ Channel ──→ PriorityQueue ──→ Semaphore ──→ IDownloader.execute()
 *                              ↑                          ↑
 *                        dispatchTrigger            withPermit 阻塞
 * ```
 */
internal class DownloadManagerImpl(
    val config: DownloadConfig,
    private val downloadDao: DownloadDao,
    private val engine: DownloadEngine = config.engine
) : DownloadManager {

    // ── 协程作用域 ──
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── 任务管理 ──
    private val downloaders = ConcurrentHashMap<String, IDownloader>()
    private val pendingDeferreds = ConcurrentHashMap<String, CompletableDeferred<DownloadStatus>>()
    private val isShutDown = AtomicBoolean(false)

    // ── Gemini 核心调度器 ──
    private val queueLock = Mutex()
    private val priorityQueue = PriorityQueue<DownloadRequest> { r1, r2 ->
        r2.priority.ordinal.compareTo(r1.priority.ordinal)
    }
    private val dispatchTrigger = Channel<Unit>(Channel.CONFLATED)
    private val semaphore = Semaphore(config.maxConcurrent)

    // ── 状态流 ──
    private val allDownloadsFlow = MutableStateFlow<List<DownloadInfo>>(emptyList())

    init {
        startRefreshLoop()
        startDispatcherLoop()
    }

    // ──────────────────────────────────────────────
    // DownloadManager 实现
    // ──────────────────────────────────────────────

    override fun enqueue(request: DownloadRequest): Disposable {
        check(!isShutDown.get()) { "DownloadManager 已关闭" }
        val id = request.calculateDownloadId()

        val deferred = CompletableDeferred<DownloadStatus>()
        pendingDeferreds[id] = deferred

        scope.launch {
            queueLock.withLock {
                priorityQueue.removeAll { it.calculateDownloadId() == id }
                priorityQueue.add(request)
            }
            dispatchTrigger.trySend(Unit)
        }

        return object : Disposable {
            override val downloadId: String = id
            override val job: Deferred<DownloadStatus> = deferred
            override val isDisposed: Boolean = deferred.isCompleted

            override fun dispose() {
                cancel(id)
                pendingDeferreds.remove(id)
                scope.launch {
                    queueLock.withLock {
                        priorityQueue.removeAll { it.calculateDownloadId() == id }
                    }
                }
            }
        }
    }

    override suspend fun execute(request: DownloadRequest): DownloadStatus {
        val disposable = enqueue(request)
        return try {
            disposable.job.await()
        } catch (e: CancellationException) {
            DownloadStatus.Cancelled(disposable.downloadId)
        } catch (e: Exception) {
            DownloadStatus.Error(disposable.downloadId, e)
        }
    }

    override fun pause(downloadId: String) {
        downloaders[downloadId]?.pause()
    }

    override fun resume(downloadId: String) {
        val downloader = downloaders[downloadId] ?: return
        scope.launch {
            try {
                downloader.resume().collect { status ->
                    handleDownloadStatus(downloadId, status)
                }
            } catch (_: CancellationException) {
                // 再次暂停或取消，不处理
            } catch (e: Exception) {
                config.logger.log(
                    DownloadLogger.LogPriority.ERROR,
                    "DownloadMgr",
                    "恢复下载失败: $downloadId",
                    e
                )
                downloadDao.updateStatus(downloadId, TaskStatus.FAILED.name)
            }
        }
    }

    override fun cancel(downloadId: String, deleteFile: Boolean) {
        downloaders[downloadId]?.cancel()
        downloaders.remove(downloadId)

        pendingDeferreds.remove(downloadId)?.complete(
            DownloadStatus.Cancelled(downloadId)
        )

        if (deleteFile) {
            scope.launch {
                val info = downloadDao.getDownloadInfo(downloadId)
                info?.let {
                    File(it.filePath).delete()
                    downloadDao.deleteChunksByTaskId(downloadId)
                    downloadDao.delete(downloadId)
                }
            }
        } else {
            scope.launch {
                downloadDao.updateStatus(downloadId, TaskStatus.CANCELLED.name)
            }
        }
    }

    override fun observeDownloadById(id: String): Flow<DownloadInfo?> {
        return allDownloadsFlow.map { list -> list.find { it.id == id } }
            .distinctUntilChanged()
    }

    override fun getAllDownloads(): Flow<List<DownloadInfo>> {
        return allDownloadsFlow.asStateFlow()
    }

    override fun searchDownloads(query: String): Flow<List<DownloadInfo>> {
        return allDownloadsFlow.map { downloads ->
            if (query.isBlank()) downloads
            else downloads.filter {
                it.fileName.contains(query, ignoreCase = true) ||
                        it.url.contains(query, ignoreCase = true)
            }
        }
    }

    override fun pauseAll() {
        downloaders.values.forEach { it.pause() }
    }

    override suspend fun cancelAll(deleteFiles: Boolean) {
        downloaders.values.forEach { it.cancel() }
        downloaders.clear()

        pendingDeferreds.forEach { (id, d) -> d.complete(DownloadStatus.Cancelled(id)) }
        pendingDeferreds.clear()

        if (deleteFiles) {
            val allDownloads = mutableListOf<DownloadInfo>()
            getAllDownloads().collect { allDownloads.addAll(it) }
            allDownloads.forEach { File(it.filePath).delete() }
            downloadDao.clearCompleted()
        }
    }

    override suspend fun cleanCompleted(deleteFiles: Boolean) {
        if (deleteFiles) {
            val completed = mutableListOf<DownloadInfo>()
            getAllDownloads().collect { list ->
                completed.addAll(list.filter { it.status == TaskStatus.COMPLETED })
            }
            completed.forEach { File(it.filePath).delete() }
        }
        downloadDao.clearCompleted()
    }

    override fun destroy() {
        if (isShutDown.compareAndSet(false, true)) {
            downloaders.values.forEach { it.cancel() }
            downloaders.clear()
            pendingDeferreds.forEach { (id, d) -> d.complete(DownloadStatus.Cancelled(id)) }
            pendingDeferreds.clear()
            scope.cancel()
        }
    }

    // ──────────────────────────────────────────────
    // 调度核心
    // ──────────────────────────────────────────────

    /**
     * 后台常驻的并发调度循环（Gemini 设计）。
     *
     * 使用 [dispatchTrigger] Channel 实现协程挂起唤醒，
     * 无任务时零 CPU 消耗。
     */
    private fun startDispatcherLoop() {
        scope.launch {
            for (trigger in dispatchTrigger) {
                if (isShutDown.get()) break

                while (isActive) {
                    val nextRequest = queueLock.withLock { priorityQueue.poll() }
                        ?: break

                    val id = nextRequest.calculateDownloadId()
                    if (downloaders.containsKey(id)) continue

                    // 为每个任务启动独立子协程去抢占信号量
                    launch {
                        try {
                            semaphore.withPermit {
                                if (!preCheckDiskSpace(nextRequest)) {
                                    val error = IOException("磁盘空间不足，无法下载 ${nextRequest.fileName}")
                                    downloadDao.updateStatus(id, TaskStatus.FAILED.name)
                                    return@withPermit
                                }

                                executeSmartDownload(nextRequest)
                            }
                        } catch (e: CancellationException) {
                            // 暂停/取消导致的取消，不处理
                        } catch (e: Exception) {
                            config.logger.log(
                                DownloadLogger.LogPriority.ERROR,
                                "DownloadMgr",
                                "下载失败: ${nextRequest.url}",
                                e
                            )
                            downloadDao.updateStatus(id, TaskStatus.FAILED.name)
                        }
                    }
                }
            }
        }
    }

    /**
     * 智能下载：根据文件大小自动选择单文件下载或分片下载。
     */
    private suspend fun executeSmartDownload(request: DownloadRequest) {
        val id = request.calculateDownloadId()

        if (!config.chunkedConfig.enabled) {
            executeSingleDownload(request)
            return
        }

        val fileSize = try {
            engine.getContentLength(request, config)
        } catch (e: Exception) {
            config.logger.log(
                DownloadLogger.LogPriority.WARN,
                "DownloadMgr",
                "无法获取文件大小，降级为单文件下载",
                e
            )
            executeSingleDownload(request)
            return
        }

        if (fileSize > config.chunkedConfig.fileSizeThreshold) {
            val supportRange = try {
                engine.supportRange(request, config)
            } catch (_: Exception) {
                false
            }
            if (supportRange) {
                executeChunkedDownload(request, fileSize)
            } else {
                config.logger.log(
                    DownloadLogger.LogPriority.INFO,
                    "DownloadMgr",
                    "服务器不支持 Range，降级为单文件下载"
                )
                executeSingleDownload(request)
            }
        } else {
            executeSingleDownload(request)
        }
    }

    /**
     * 单文件下载 —— 委托 [SingleDownloader]。
     */
    private suspend fun executeSingleDownload(request: DownloadRequest) {
        val id = request.calculateDownloadId()
        val existingInfo = downloadDao.getDownloadInfo(id)

        if (existingInfo == null) {
            val file = File(request.filePath, request.fileName)
            file.parentFile?.mkdirs()
            val info = DownloadInfo(
                id = id, url = request.url, fileName = request.fileName,
                filePath = file.absolutePath, status = TaskStatus.DOWNLOADING,
                headers = request.headers, priority = request.priority
            )
            downloadDao.insertOrUpdate(info.toEntity())
        } else {
            downloadDao.updateStatus(id, TaskStatus.DOWNLOADING.name)
        }

        val downloader = SingleDownloader(request, config, engine)
        downloaders[id] = downloader

        try {
            downloader.execute(
                existingEtag = existingInfo?.etag,
                existingLastModified = existingInfo?.lastModified
            ).collect { status -> handleDownloadStatus(id, status) }
        } finally {
            downloaders.remove(id)
        }
    }

    /**
     * 分片下载。
     */
    private suspend fun executeChunkedDownload(request: DownloadRequest, fileSize: Long) {
        val id = request.calculateDownloadId()
        val existingInfo = downloadDao.getDownloadInfo(id)

        if (existingInfo == null) {
            val file = File(request.filePath, request.fileName)
            file.parentFile?.mkdirs()
            val info = DownloadInfo(
                id = id, url = request.url, fileName = request.fileName,
                filePath = file.absolutePath, totalBytes = fileSize,
                status = TaskStatus.DOWNLOADING, headers = request.headers,
                priority = request.priority
            )
            downloadDao.insertOrUpdate(info.toEntity())
        } else {
            downloadDao.updateStatus(id, TaskStatus.DOWNLOADING.name)
        }

        val downloader = ChunkedDownloader(request, fileSize, config, downloadDao)
        downloaders[id] = downloader

        try {
            downloader.execute(
                existingEtag = existingInfo?.etag,
                existingLastModified = existingInfo?.lastModified
            ).collect { status -> handleDownloadStatus(id, status) }
        } finally {
            downloaders.remove(id)
        }
    }

    // ──────────────────────────────────────────────
    // 辅助方法
    // ──────────────────────────────────────────────

    /**
     * 公共状态处理 —— 被 [executeSingleDownload]、[executeChunkedDownload] 和 [resume] 共用。
     */
    private suspend fun handleDownloadStatus(id: String, status: DownloadStatus) {
        when (status) {
            is DownloadStatus.Progress -> {
                downloadDao.updateProgress(id, status.downloadedBytes, status.totalBytes)
            }
            is DownloadStatus.Success -> {
                downloadDao.updateStatus(id, TaskStatus.COMPLETED.name)
                pendingDeferreds.remove(id)?.complete(status)
            }
            is DownloadStatus.Error -> {
                downloadDao.updateStatus(id, TaskStatus.FAILED.name)
                pendingDeferreds.remove(id)?.complete(status)
            }
            is DownloadStatus.Cancelled -> {
                downloadDao.updateStatus(id, TaskStatus.CANCELLED.name)
                pendingDeferreds.remove(id)?.complete(status)
            }
            is DownloadStatus.Paused -> {
                downloadDao.updateStatus(id, TaskStatus.PAUSED.name)
                pendingDeferreds.remove(id)?.complete(status)
            }
            else -> {}
        }
    }

    /**
     * 磁盘空间预检。
     */
    private suspend fun preCheckDiskSpace(request: DownloadRequest): Boolean {
        val file = File(request.filePath, request.fileName)
        val parentDir = file.parentFile ?: return true
        if (!parentDir.exists()) parentDir.mkdirs()

        val id = request.calculateDownloadId()
        val info = downloadDao.getDownloadInfo(id)
        val totalBytes = info?.totalBytes ?: 0L
        val downloadedBytes = info?.downloadedBytes ?: 0L

        if (totalBytes > 0) {
            val remainingBytes = totalBytes - downloadedBytes
            val usableSpace = parentDir.usableSpace
            if (usableSpace < remainingBytes) {
                config.logger.log(
                    DownloadLogger.LogPriority.WARN,
                    "DownloadMgr",
                    "磁盘空间不足: 需要 ${remainingBytes / 1024 / 1024}MB, 可用 ${usableSpace / 1024 / 1024}MB"
                )
                return false
            }
        }
        return true
    }

    /**
     * 常驻刷新协程 —— Room Flow 自动监听表变化并推送最新数据。
     */
    private fun startRefreshLoop() {
        scope.launch {
            try {
                downloadDao.getAllDownloads().collect { downloads ->
                    allDownloadsFlow.value = downloads
                }
            } catch (_: Exception) {
                // Room 查询异常时静默处理
            }
        }
    }
}
