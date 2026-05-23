package com.taisau.android.common.upgrade

import kotlinx.coroutines.flow.Flow

/**
 * 升级状态，描述当前升级流程正在执行或等待的步骤。
 *
 * [UpgradeManager.state] 对外暴露此状态的 [kotlinx.coroutines.flow.StateFlow]，
 * 调用方可以 collect 此状态来驱动 UI 变化或自动执行后续步骤。
 *
 * 状态流转图（非自动模式）：
 * ```
 * Idle → Checking → NewVersionReady ─→ Downloading ─→ DownloadCompleted ─→ Installing ─→ Installed
 *                    NoNewVersion ────→ Idle                                  InstallFailed
 *                    CheckFailed ─────→ Idle                                  ↑
 *                                        DownloadFailed ──────────────────────┘
 * ```
 *
 * 在 [UpdateMode.AUTO_DOWNLOAD] 模式下，NewVersionReady → Downloading → DownloadCompleted → Installing
 * 的转换由 [UpgradeManagerImpl] 内部自动完成。
 */
sealed interface UpgradeState {

    /** 空闲状态（初始状态，或一次完整流程结束后）。 */
    data object Idle : UpgradeState

    /** 正在检测新版本。 */
    data object Checking : UpgradeState

    /** 检测完成，发现新版本。 [info] 包含版本详情。 */
    data class NewVersionReady(val info: UpgradeInfo) : UpgradeState

    /** 检测完成，无新版本。 */
    data object NoNewVersion : UpgradeState

    /** 检测失败。 */
    data class CheckFailed(val error: Throwable) : UpgradeState

    /** 正在下载 APK。 */
    data class Downloading(
        val progress: Float = 0f,
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = 0,
        val speed: Float
    ) : UpgradeState

    /** 下载完成。[savePath] 为 APK 文件的本地完整路径。 */
    data class DownloadCompleted(val savePath: String) : UpgradeState

    /** 下载失败。 */
    data class DownloadFailed(val error: Throwable) : UpgradeState

    /** 正在安装 APK。 */
    data object Installing : UpgradeState

    /** 安装完成。 */
    data object Installed : UpgradeState

    /** 安装失败。 */
    data class InstallFailed(val error: Throwable) : UpgradeState
}
