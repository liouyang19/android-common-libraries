package com.taisau.android.common.download

import com.taisau.android.common.download.db.DownloadDao
import com.taisau.android.common.download.download.DownloadRequest
import com.taisau.android.common.download.engine.DownloadEngine
import com.taisau.android.common.download.utils.DefaultDownloadLogger
import com.taisau.android.common.download.utils.DownloadLogger

/**
 * 下载管理器的配置参数。
 *
 * 通过 [DownloadManager.Builder] 构建后传入 [DownloadManagerImpl]。
 */
data class DownloadConfig(
    val engine: DownloadEngine,
    val downloadDao: DownloadDao,
    val maxConcurrent: Int = 3,
    val maxRetries: Int = 3,
    val connectTimeout: Long = 15_000L,
    val readTimeout: Long = 15_000L,
    val defaultFilePath: String,
    val logger: DownloadLogger = DefaultDownloadLogger(),
    val interceptor: DownloadInterceptor? = null,
    val chunkedConfig: ChunkedConfig = ChunkedConfig()
)

/**
 * 分片下载配置。
 *
 * @property enabled              是否启用分片下载
 * @property fileSizeThreshold    启用分片下载的文件大小阈值（默认 10MB）
 * @property chunkSize            每个分片的大小（默认 5MB）
 * @property maxParallelChunks    最大并行分片数
 */
data class ChunkedConfig(
    val enabled: Boolean = true,
    val fileSizeThreshold: Long = 10 * 1024 * 1024L, // 10MB
    val chunkSize: Long = 5 * 1024 * 1024L,           // 5MB
    val maxParallelChunks: Int = 3
)

/**
 * 下载拦截器 —— 允许在下载前后插入自定义逻辑。
 */
interface DownloadInterceptor {
    suspend fun intercept(chain: Chain): DownloadStatus

    interface Chain {
        val request: DownloadRequest
        suspend fun proceed(request: DownloadRequest): DownloadStatus
    }
}
