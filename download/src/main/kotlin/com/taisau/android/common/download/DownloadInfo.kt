package com.taisau.android.common.download

/**
 * 下载记录的核心数据模型。
 *
 * 区别于 [DownloadStatus]（Flow 发射的瞬时状态），
 * [DownloadInfo] 是持久化视图，反映数据库中的记录快照。
 */
data class DownloadInfo(
    /** 系统分配的唯一下载 ID（url+path+fileName 的 MD5）。 */
    val id: String,

    /** 下载地址。 */
    val url: String,

    /** 保存的文件名。 */
    val fileName: String = "",

    /** 文件的完整路径（含文件名）。 */
    val filePath: String = "",

    /** 文件总大小（字节）。 */
    val totalBytes: Long = 0,

    /** 已下载字节数。 */
    val downloadedBytes: Long = 0,

    /** 当前状态。 */
    val status: TaskStatus = TaskStatus.PENDING,

    /** 自定义请求头（序列化后的 Map）。 */
    val headers: Map<String, String> = emptyMap(),

    /** 错误信息。 */
    val errorMessage: String? = null,

    /** 记录创建时间。 */
    val createTime: Long = System.currentTimeMillis(),

    /** 最后更新时间。 */
    val updateTime: Long = System.currentTimeMillis(),

    /** 下载优先级。 */
    val priority: DownloadPriority = DownloadPriority.NORMAL,

    /** 服务端 ETag，用于检测文件是否已变更。 */
    val etag: String? = null,

    /** 服务端 Last-Modified，用于检测文件是否已变更。 */
    val lastModified: String? = null
)
