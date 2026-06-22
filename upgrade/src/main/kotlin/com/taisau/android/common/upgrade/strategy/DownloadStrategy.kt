package com.taisau.android.common.upgrade.strategy

import com.taisau.android.common.upgrade.UpgradeState
import kotlinx.coroutines.flow.Flow

/**
 * APK 下载策略接口。
 *
 * 通过 [Flow] 持续上报下载进度，调用方（[DownloadWorker]）collect 此 flow
 * 即可获得实时进度并通过 [setProgress] 传递给 [UpgradeManagerImpl]。
 *
 * Flow 约定：
 * - **正常运行**：持续 emit [UpgradeState.Downloading]（含 bytesDownloaded / totalBytes），完成后自动结束
 * - **下载失败**：抛出异常
 * - **取消/中断**：协程取消 ([CancellationException])
 *
 * @see DefaultDownloadStrategy 默认实现（DownloadManager + flow 轮询）
 */
interface DownloadStrategy {

    /**
     * 下载 APK 到指定路径，返回进度流。
     *
     * @param downloadUrl APK 下载地址
     * @param savePath APK 存放的本地文件路径
     * @return 下载进度流，emit 实时进度，结束后自动完成
     */
    fun download(downloadUrl: String, savePath: String): Flow<UpgradeState.Downloading>
}
