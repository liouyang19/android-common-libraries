package com.taisau.android.common.upgrade.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException

/**
 * WorkManager 后台任务：下载 APK。
 *
 * 通过 [StrategyHolder] 获取 [DownloadStrategy]，collect 其返回的
 * [Flow][kotlinx.coroutines.flow.Flow] 来获得实时进度，
 * 并通过 [setProgress] 上报给 [UpgradeManagerImpl]。
 *
 * 输入参数：
 * - [KEY_DOWNLOAD_URL]：下载地址
 * - [KEY_SAVE_PATH]：APK 存放的本地完整路径
 *
 * 进度上报（通过 [setProgress]）：
 * - [PROGRESS_BYTES_DOWNLOADED]：已下载字节数
 * - [PROGRESS_TOTAL_BYTES]：总字节数
 *
 * 输出：
 * - [OUTPUT_SAVE_PATH]：APK 存放路径（同输入）
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_SAVE_PATH = "save_path"

        const val OUTPUT_SAVE_PATH = "save_path"
        const val OUTPUT_SUCCESS = "download_success"

        /** setProgress key：已下载字节数 */
        const val PROGRESS_BYTES_DOWNLOADED = "bytes_downloaded"
        /** setProgress key：总字节数 */
        const val PROGRESS_TOTAL_BYTES = "total_bytes"
    }

    override suspend fun doWork(): Result {
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL)
            ?: return Result.failure()
        val savePath = inputData.getString(KEY_SAVE_PATH)
            ?: return Result.failure()

        val strategy = StrategyHolder.getDownload()

        return try {
            // collect 策略返回的进度流，每收到一次更新上报至 WorkManager
            strategy.download(downloadUrl, savePath).collect { progress ->
                setProgress(
                    workDataOf(
                        PROGRESS_BYTES_DOWNLOADED to progress.bytesDownloaded,
                        PROGRESS_TOTAL_BYTES to progress.totalBytes,
                    )
                )
            }
            // Flow 正常结束 → 下载完成
            Result.success(
                workDataOf(
                    OUTPUT_SAVE_PATH to savePath,
                    OUTPUT_SUCCESS to true,
                )
            )
        } catch (e: CancellationException) {
            throw e // WorkManager 处理取消
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: "Download failed")))
        }
    }
}
