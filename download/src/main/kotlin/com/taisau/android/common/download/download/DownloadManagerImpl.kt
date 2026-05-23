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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
 * enqueue(request) ──→ Channel ──→ PriorityQueue ──→ Semaphore ──→ DownloadTask.execute()
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
    private val ioDispatcher = Dispatchers.IO

    // ── 任务管理 ──
    private val tasks = ConcurrentHashMap<String, DownloadTask>()
    private val chunkedTasks = ConcurrentHashMap<String, ChunkedDownloader>()
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
    private val refreshTrigger = Channel<Unit>(Channel.CONFLATED)

    init {
        startRefreshLoop()
        refreshTrigger.trySend(Unit)
        startDispatcherLoop()
    }

    // ──────────────────────────────────────────────
    // DownloadManager 实现
    // ──────────────────────────────────────────────

    override fun enqueue(request: DownloadRequest): Disposable {
        check(!isShutDown.get()) { "DownloadManager 已关闭" }
        val id = request.calculateDownloadId()

        // 线程安全地加入优先级队列
        scope.launch(ioDispatcher) {
            queueLock.withLock {
                priorityQueue.removeAll { it.calculateDownloadId() == id }
                priorityQueue.add(request)
            }
            dispatchTrigger.trySend(Unit)
        }

        // 创建 Deferred 用于调用方等待
        val deferred = scope.async(ioDispatcher) {
            // 实际由调度器执行，此处仅作占位
            DownloadStatus.Waiting(id)
        }

        return object : Disposable {
            override val downloadId: String = id
            override val job: Deferred<DownloadStatus> = deferred
            override val isDisposed: Boolean = !tasks.containsKey(id) && !chunkedTasks.containsKey(id)

            override fun dispose() {
                cancel(id)
                scope.launch(ioDispatcher) {
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
        tasks[downloadId]?.let { /* 单文件下载不支持暂停，通过协程取消实现 */ }
        chunkedTasks[downloadId]?.pause()
    }

    override fun cancel(downloadId: String, deleteFile: Boolean) {
        tasks.remove(downloadId)
        chunkedTasks[downloadId]?.cancel()
        chunkedTasks.remove(downloadId)

        if (deleteFile) {
            scope.launch(ioDispatcher) {
                val info = downloadDao.getDownloadInfo(downloadId)
                info?.let {
                    File(it.filePath).delete()
                    downloadDao.deleteChunksByTaskId(downloadId)
                    downloadDao.delete(downloadId)
                }
            }
        } else {
            scope.launch(ioDispatcher) {
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
        chunkedTasks.values.forEach { it.pause() }
    }

    override suspend fun cancelAll(deleteFiles: Boolean) {
        tasks.clear()
        chunkedTasks.values.forEach { it.cancel() }
        chunkedTasks.clear()

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
        refreshAllDownloads()
    }

    override fun destroy() {
        if (isShutDown.compareAndSet(false, true)) {
            tasks.clear()
            chunkedTasks.values.forEach { it.cancel() }
            chunkedTasks.clear()
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
        scope.launch(ioDispatcher) {
            for (trigger in dispatchTrigger) {
                if (isShutDown.get()) break

                while (isActive) {
                    // 从优先级队列取出最高权重的请求
                    val nextRequest = queueLock.withLock { priorityQueue.poll() }
                        ?: break // 队列为空，挂起等待下一次 dispatchTrigger

                    val id = nextRequest.calculateDownloadId()
                    if (tasks.containsKey(id) && !chunkedTasks.containsKey(id)) continue

                    // 为每个任务启动独立子协程去抢占信号量
                    launch {
                        try {
                            semaphore.withPermit {
                                // 磁盘空间预检
                                if (!preCheckDiskSpace(nextRequest)) {
                                    val error = IOException("磁盘空间不足，无法下载 ${nextRequest.fileName}")
                                    downloadDao.updateStatus(id, TaskStatus.FAILED.name)
                                    refreshAllDownloads()
                                    return@withPermit
                                }

                                // 智能选择下载方式
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
                            refreshAllDownloads()
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

        // 检查是否启用分片下载
        if (!config.chunkedConfig.enabled) {
            executeSingleDownload(request)
            return
        }

        // 获取文件大小
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

        // 根据阈值决定下载方式
        if (fileSize > config.chunkedConfig.fileSizeThreshold) {
            // 检查是否支持 Range
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
     * 单文件下载。
     */
    private suspend fun executeSingleDownload(request: DownloadRequest) {
        val id = request.calculateDownloadId()
        val existingInfo = downloadDao.getDownloadInfo(id)

        // 创建/更新下载记录
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
        refreshAllDownloads()

        val task = DownloadTask(request, config, engine)
        tasks[id] = task

        try {
            task.execute(
                existingEtag = existingInfo?.etag,
                existingLastModified = existingInfo?.lastModified
            ).collect { status ->
                // 持久化进度
                when (status) {
                    is DownloadStatus.Progress -> {
                        downloadDao.updateProgress(id, status.downloadedBytes, status.totalBytes)
                        refreshAllDownloads()
                    }
                    is DownloadStatus.Success -> {
                        downloadDao.updateStatus(id, TaskStatus.COMPLETED.name)
                        refreshAllDownloads()
                    }
                    else -> {}
                }
            }
        } finally {
            tasks.remove(id)
        }
    }

    /**
     * 分片下载。
     */
    private suspend fun executeChunkedDownload(request: DownloadRequest, fileSize: Long) {
        val id = request.calculateDownloadId()
        val existingInfo = downloadDao.getDownloadInfo(id)

        // 创建/更新下载记录
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
        refreshAllDownloads()

        val chunkedDownloader = ChunkedDownloader(request, fileSize, config, downloadDao)
        chunkedTasks[id] = chunkedDownloader

        try {
            chunkedDownloader.execute(
                existingEtag = existingInfo?.etag,
                existingLastModified = existingInfo?.lastModified
            ).collect { status ->
                when (status) {
                    is DownloadStatus.Progress -> {
                        downloadDao.updateProgress(id, status.downloadedBytes, status.totalBytes)
                        refreshAllDownloads()
                    }
                    is DownloadStatus.Success -> {
                        downloadDao.updateStatus(id, TaskStatus.COMPLETED.name)
                        refreshAllDownloads()
                    }
                    else -> {}
                }
            }
        } finally {
            chunkedTasks.remove(id)
        }
    }

    // ──────────────────────────────────────────────
    // 辅助方法
    // ──────────────────────────────────────────────

    /**
     * 磁盘空间预检 —— 在开始下载前检查剩余空间是否足够。
     *
     * 可防止下载到 99% 时因空间不足而崩溃（Gemini 优化点 2）。
     */
    private suspend fun preCheckDiskSpace(request: DownloadRequest): Boolean {
        val file = File(request.filePath, request.fileName)
        val parentDir = file.parentFile ?: return true
        if (!parentDir.exists()) parentDir.mkdirs()

        // 从数据库获取文件大小
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
     * 常驻刷新协程 —— 利用 Room Flow 的自动更新特性，只需 collect 一次。
     */
    private fun startRefreshLoop() {
        scope.launch(ioDispatcher) {
            try {
                downloadDao.getAllDownloads().collect { downloads ->
                    allDownloadsFlow.value = downloads
                }
            } catch (_: Exception) {
                // Room 查询异常时静默处理
            }
        }
    }

    /**
     * 触发立即刷新 —— 仅在有独立 collector 前用于初始加载。
     * 内部 collector 建立后 Room Flow 会自动推送变更。
     */
    private fun refreshAllDownloads() {
        refreshTrigger.trySend(Unit)
    }
}
