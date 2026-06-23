@file:Suppress("unused")

package com.taisau.android.common.download

import android.content.Context
import com.taisau.android.common.download.db.DownloadDao
import com.taisau.android.common.download.db.RoomDownloadDao
import com.taisau.android.common.download.download.DownloadManagerImpl
import com.taisau.android.common.download.download.DownloadRequest
import com.taisau.android.common.download.engine.DownloadEngine
import com.taisau.android.common.download.engine.OkHttpEngine
import com.taisau.android.common.download.utils.DownloadLogger
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * 下载管理器接口 —— 定义的公开 API 契约。
 *
 * 推荐通过 [Context.downloadManager] 扩展属性获取全局单例：
 * ```
 * // 1. 在 Application 中实现 [SingletonDownloadManager.Factory]
 * class MyApp : Application(), SingletonDownloadManager.Factory {
 *     override fun newDownloadManager(context: Context): DownloadManager {
 *         return DownloadManager.Builder(context)
 *             .setDefaultFilePath(filesDir.absolutePath)
 *             .setMaxConcurrentDownloads(5)
 *             .build()
 *     }
 * }
 *
 * // 2. 获取单例并下载
 * val manager = context.downloadManager
 * val disposable = manager.enqueue(DownloadRequest(url = "...", fileName = "file.zip"))
 * ```
 */
interface DownloadManager {

    // ── 提交下载 ──

    /**
     * 提交下载任务（非挂起，秒级返回凭证）。
     *
     * 内部使用 [Channel][kotlinx.coroutines.channels.Channel] 实现并行 enqueue，
     * 实际下载由调度器串行/并发执行。
     *
     * @param request 下载请求参数
     * @return [Disposable] 凭证，包含 [Disposable.downloadId] 用于后续控制
     */
    fun enqueue(request: DownloadRequest): Disposable

    /**
     * 提交下载并挂起等待完成。
     *
     * @param request 下载请求参数
     * @return [DownloadStatus] 最终状态
     */
    suspend fun execute(request: DownloadRequest): DownloadStatus

    // ── 控制 ──

    /** 暂停指定下载。 */
    fun pause(downloadId: String)

    /**
     * 恢复已暂停的下载。
     *
     * 仅当 [DownloadManager] 内存中仍持有该下载器实例时有效
     *（最近刚 [pause] 但尚未被移除）。若下载器已被清理，请重新 [enqueue]。
     */
    fun resume(downloadId: String)

    /**
     * 取消指定下载。
     *
     * @param downloadId 下载 ID
     * @param deleteFile 是否同时删除已下载的文件
     */
    fun cancel(downloadId: String, deleteFile: Boolean = false)

    // ── 查询 ──

    /**
     * 观察指定下载的实时状态。
     *
     * @param id [enqueue] 返回的 [Disposable.downloadId]
     * @return 持续发射 [DownloadInfo] 的 Flow
     */
    fun observeDownloadById(id: String): Flow<DownloadInfo?>

    /**
     * 获取所有下载记录（响应式 Flow）。
     */
    fun getAllDownloads(): Flow<List<DownloadInfo>>

    /**
     * 搜索下载记录（按文件名或 URL 模糊匹配）。
     */
    fun searchDownloads(query: String): Flow<List<DownloadInfo>>

    // ── 批量操作 ──

    /** 暂停所有正在下载的任务。 */
    fun pauseAll()

    /**
     * 取消所有下载任务。
     *
     * @param deleteFiles 是否同时删除已下载的文件
     */
    suspend fun cancelAll(deleteFiles: Boolean = false)

    /**
     * 清理已完成的记录。
     *
     * @param deleteFiles 是否同时删除已完成文件
     */
    suspend fun cleanCompleted(deleteFiles: Boolean = false)

    // ── 生命周期 ──

    /** 释放内部协程作用域。不再使用此管理器时应调用。 */
    fun destroy()

    // ────────────────────────────────────────────
    // Builder
    // ────────────────────────────────────────────

    class Builder(private val context: Context) {
        private var outputDir: String = ""
        private var maxConcurrent: Int = 3
        private var maxRetries: Int = 3
        private var connectTimeout: Int = 15_000
        private var readTimeout: Int = 15_000
        private var engine: DownloadEngine = OkHttpEngine()
        
        private var logger: Logger = DownloadLogger(false)
        private var downloadDao: DownloadDao = RoomDownloadDao(context)
        private var chunkedEnabled: Boolean = true
        private var chunkedFileSizeThreshold: Long = 10 * 1024 * 1024L
        private var chunkedChunkSize: Long = 5 * 1024 * 1024L
        private var chunkedMaxParallel: Int = 3

        /** 注入自定义 [DownloadDao] 实现。默认使用 [RoomDownloadDao]。 */
        fun setDownloadDao(dao: DownloadDao) = apply { this.downloadDao = dao }
        fun setEngine(engine: DownloadEngine) = apply { this.engine = engine }
        fun setDefaultFilePath(path: String) = apply { outputDir = path }
        fun setMaxConcurrentDownloads(max: Int) = apply {
            maxConcurrent = max.coerceIn(1, 10)
        }
        fun setMaxRetries(retries: Int) = apply {
            maxRetries = retries.coerceAtLeast(0)
        }
        fun setConnectTimeout(timeout: Int) = apply { connectTimeout = timeout }
        fun setReadTimeout(timeout: Int) = apply { readTimeout = timeout }
        
        fun enableLogs(enabled: Boolean) = apply {
            this.logger = DownloadLogger(enabled)
        }
        fun setLogger(logger: Logger) = apply {
            this.logger = logger
        }

        /** 配置分片下载 */
        fun setChunkedConfig(
            enabled: Boolean = true,
            fileSizeThreshold: Long = 10 * 1024 * 1024L,
            chunkSize: Long = 5 * 1024 * 1024L,
            maxParallel: Int = 3
        ) = apply {
            this.chunkedEnabled = enabled
            this.chunkedFileSizeThreshold = fileSizeThreshold
            this.chunkedChunkSize = chunkSize
            this.chunkedMaxParallel = maxParallel
        }

        fun build(): DownloadManager {
            val appContext = context.applicationContext
            val finalPath = if (outputDir.isEmpty()) {
                val dir = File(appContext.cacheDir, "downloads")
                dir.mkdirs()
                dir.absolutePath
            } else outputDir

            val config = DownloadConfig(
                engine = engine,
                downloadDao = downloadDao,
                maxConcurrent = maxConcurrent,
                maxRetries = maxRetries,
                connectTimeout = connectTimeout.toLong(),
                readTimeout = readTimeout.toLong(),
                defaultFilePath = finalPath,
                chunkedConfig = ChunkedConfig(
                    enabled = chunkedEnabled,
                    fileSizeThreshold = chunkedFileSizeThreshold,
                    chunkSize = chunkedChunkSize,
                    maxParallelChunks = chunkedMaxParallel
                )
            )
            return DownloadManagerImpl(config, downloadDao,engine ,logger)
        }
    }
}
