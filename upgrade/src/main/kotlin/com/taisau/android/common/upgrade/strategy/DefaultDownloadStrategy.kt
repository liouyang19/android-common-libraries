package com.taisau.android.common.upgrade.strategy

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.taisau.android.common.upgrade.UpgradeState
import java.io.IOException

/**
 * [DownloadStrategy] 的默认实现。
 *
 * 使用系统 [DownloadManager] 下载 APK，内部通过 `flow { }` 轮询 [DownloadManager]
 * 持续 emit [UpgradeState.Downloading] 实时进度。
 *
 * @param context Application Context
 */
class DefaultDownloadStrategy(
    private val context: Context,
) : DownloadStrategy {

    override fun download(downloadUrl: String, savePath: String): Flow<UpgradeState.Downloading> = flow {
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("下载更新")
            setDescription("正在下载更新")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.parse("file://$savePath"))
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        // 轮询 DownloadManager 直至下载完成
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            manager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw IOException("Download not found: $downloadId")
                }

                val status = cursor.getInt(
                    cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                )

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val total = cursor.getLong(
                            cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        emit(
                            UpgradeState.Downloading(
                                progress = 1f,
                                bytesDownloaded = total,
                                totalBytes = total,
                                speed = 0f,
                            )
                        )
                        return@flow
                    }

                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(
                            cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        )
                        throw IOException("Download failed: reason=$reason")
                    }

                    else -> {
                        val downloaded = cursor.getLong(
                            cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        )
                        val total = cursor.getLong(
                            cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        val progress = if (total > 0) downloaded.toFloat() / total else 0f
                        emit(
                            UpgradeState.Downloading(
                                progress = progress,
                                bytesDownloaded = downloaded,
                                totalBytes = total,
                                speed = 0f,
                            )
                        )
                    }
                }
            }
            delay(1000L)
        }
    }
}
