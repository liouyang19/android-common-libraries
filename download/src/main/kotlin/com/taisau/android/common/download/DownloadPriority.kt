package com.taisau.android.common.download

/**
 * 下载优先级 —— 用于 [DownloadRequest.priority]。
 *
 * 优先级高的任务会在队列中被优先调度执行。
 */
enum class DownloadPriority {
    LOW,
    NORMAL,
    HIGH
}
