package com.taisau.android.common.download.download

import com.taisau.android.common.download.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * 下载器接口 —— 代表**一个下载文件**的完整生命周期。
 *
 * 两种实现：
 * - [SingleDownloader]：单文件（不分片）下载
 * - [ChunkedDownloader]：大文件分片下载（内部创建多个 [DownloadTask] 写临时文件后合并）
 *
 * 上层（[DownloadManagerImpl]）通过此接口统一管理，无需关心内部实现。
 */
internal interface IDownloader {
    val downloadId: String

    /**
     * 执行下载，返回实时状态 Flow。
     */
    suspend fun execute(
        existingEtag: String? = null,
        existingLastModified: String? = null
    ): Flow<DownloadStatus>

    /**
     * 恢复下载 —— 清除暂停标记，使用上次 [execute] 传入的 etag/lastModified 重新执行。
     *
     * 仅当下载器实例仍存在于内存中（即刚被 [pause] 但尚未被 [cancel] / destroy 移除）时有效。
     *
     * @return 新的状态 Flow，调用方需 [collect][kotlinx.coroutines.flow.Flow.collect]
     */
    suspend fun resume(): Flow<DownloadStatus>

    /** 暂停下载。 */
    fun pause()

    /** 取消下载。 */
    fun cancel()
}
