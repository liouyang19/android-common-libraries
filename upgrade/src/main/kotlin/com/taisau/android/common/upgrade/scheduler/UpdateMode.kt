package com.taisau.android.common.upgrade.scheduler

/**
 * 更新模式，定义检测到新版本后的行为。
 *
 * 通过 [UpgradeScheduleConfig.updateMode] 配置。
 *
 * - [NOTIFY_ONLY]：仅发送新版本通知，由用户手动操作（下载/安装）
 * - [CONFIRM_DOWNLOAD]：发送带"下载"按钮的通知，用户点击后开始下载，下载完成后自动触发安装
 * - [AUTO_DOWNLOAD]：自动下载新版本 APK，下载完成后自动触发安装
 */
enum class UpdateMode {
    /** 仅通知用户有新版本，由用户手动操作  */
    NOTIFY_ONLY,

    /** 通知用户并附带下载按钮，用户确认后下载安装 */
    CONFIRM_DOWNLOAD,

    /** 检测到新版本后自动下载安装，无需用户干预 */
    AUTO_DOWNLOAD,
}
