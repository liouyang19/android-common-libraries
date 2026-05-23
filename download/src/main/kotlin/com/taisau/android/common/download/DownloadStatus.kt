package com.taisau.android.common.download

/**
 * 下载状态 —— 通过 Flow 发射给观察者。
 *
 * 使用 sealed class 而非 enum，以便携带不同状态下的上下文数据。
 */
sealed class DownloadStatus {

    /** 下载中（含当前进度）。 */
    data class Progress(
        /** 下载 ID。 */
        val downloadId: String,
        /** 下载地址。 */
        val url: String,
        /** 已下载字节数。 */
        val downloadedBytes: Long,
        /** 文件总字节数。 */
        val totalBytes: Long,
        /** 瞬时下载速度（字节/秒）。 */
        val speed: Long = 0,
        /** 进度百分比（0f ~ 100f）。 */
        val percent: Float = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes * 100).coerceIn(0f, 100f)
        } else 0f
    ) : DownloadStatus()

    /** 下载成功。 */
    data class Success(
        val downloadId: String,
        val filePath: String
    ) : DownloadStatus()

    /** 下载出错。 */
    data class Error(
        val downloadId: String,
        val error: Throwable
    ) : DownloadStatus()

    /** 已暂停。 */
    data class Paused(val downloadId: String) : DownloadStatus()

    /** 已取消。 */
    data class Cancelled(val downloadId: String) : DownloadStatus()

    /** 排队等待中。 */
    data class Waiting(val downloadId: String) : DownloadStatus()
}

/**
 * 数据库持久化用的下载状态枚举。
 *
 * 与 [DownloadStatus] sealed class 区分，仅用于 Room 存贮。
 */
enum class TaskStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

// ── 转换扩展 ──

/** 将数据库存储的状态字符串解析为 [TaskStatus]。 */
internal fun String.toTaskStatus(): TaskStatus = try {
    TaskStatus.valueOf(this)
} catch (_: IllegalArgumentException) {
    TaskStatus.PENDING
}

/** 将 [TaskStatus] 转为数据库存储的字符串。 */
internal fun TaskStatus.toStatusString(): String = name
