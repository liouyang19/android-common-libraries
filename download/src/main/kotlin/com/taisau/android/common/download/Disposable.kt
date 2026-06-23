package com.taisau.android.common.download

import kotlinx.coroutines.Deferred

/**
 * [DownloadManager.enqueue] 返回的下载凭证。
 *
 * 持有 [downloadId] 用于后续控制（暂停/取消/观察），
 * 以及 [job] 用于等待下载完成。
 */
interface Disposable {
    /** 系统分配的下载唯一 ID。 */
    val downloadId: String

    /** 下载结果，await 后返回 [DownloadStatus]。 */
    val job: Deferred<DownloadStatus>

    /** 是否已被释放（取消/完成）。 */
    val isDisposed: Boolean

    /** 释放此下载，取消正在进行的传输并清理资源。 */
    fun dispose()
}


