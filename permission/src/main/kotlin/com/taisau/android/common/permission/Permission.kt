package com.taisau.android.common.permission

import android.Manifest
import android.os.Build

sealed class Permission(val rawPermissions: List<String>) {
    object Camera : Permission(listOf(Manifest.permission.CAMERA))
    object Notifications : Permission(
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyList()
    )
    object LocationFine : Permission(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    object LocationCoarse : Permission(listOf(Manifest.permission.ACCESS_COARSE_LOCATION))
    object Location : Permission(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    )
    object RecordAudio : Permission(listOf(Manifest.permission.RECORD_AUDIO))

    object MediaImages : Permission(listOf(Manifest.permission.READ_MEDIA_IMAGES))
    object MediaVideo : Permission(listOf(Manifest.permission.READ_MEDIA_VIDEO))
    object MediaAudio : Permission(listOf(Manifest.permission.READ_MEDIA_AUDIO))

    object Storage : Permission(
        if (Build.VERSION.SDK_INT >= 33) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    )
    object WriteExternalStorage : Permission(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))

    /**
     * Android 11+ (API 30) 特殊权限，管理所有文件访问。
     * 不通过标准权限弹窗申请，需跳转系统设置页。
     */
    object ManageExternalStorage : Permission(emptyList())

    /**
     * 安装包权限，允许安装未知来源应用。
     * Android 8+ (API 26+) 需跳转系统设置页授予。
     */
    object InstallPackages : Permission(emptyList())

    /**
     * 系统设置权限，允许修改系统设置。
     * Android 6+ (API 23+) 需跳转系统设置页授予。
     */
    object WriteSettings : Permission(emptyList())

    /**
     * 悬浮窗权限，允许在其他应用上层显示。
     * Android 6+ (API 23+) 需跳转系统设置页授予。
     */
    object SystemAlertWindow : Permission(emptyList())

    /**
     * 读取应用使用统计权限，需跳转系统设置页授予。
     * Android 5+ (API 21+) 需跳转"有权查看使用情况的应用"设置页。
     */
    object PackageUsageStats : Permission(emptyList())

    /**
     * 精确闹钟权限，允许设置精确响铃的闹钟。
     * Android 12+ (API 31+) 需跳转系统设置页授予。
     */
    object ScheduleExactAlarm : Permission(emptyList())

    /**
     * 画中画权限，允许以小窗口模式显示。
     * Android 8+ (API 26+) 可通过系统设置页管理。
     */
    object PictureInPicture : Permission(emptyList())

    /**
     * 无障碍权限，允许辅助功能服务读取屏幕内容。
     * 需跳转无障碍设置页由用户手动开启。
     */
    object Accessibility : Permission(emptyList())
}
