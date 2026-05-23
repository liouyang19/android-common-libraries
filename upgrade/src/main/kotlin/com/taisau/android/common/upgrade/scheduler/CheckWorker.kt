package com.taisau.android.common.upgrade.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.taisau.android.common.upgrade.UpgradeInfo

/**
 * WorkManager 后台任务：检测新版本。
 *
 * 由 [UpgradeScheduler] 定时调度，或由 [UpgradeManager.check] 创建一键任务使用。
 *
 * 功能：
 * - 通过 [StrategyHolder] 获取 [CheckStrategy] 执行版本检测
 * - 支持时间窗口校验（定时调度场景）
 * - 根据 [UpdateMode] 发送通知或自动触发下载
 *
 * @see CheckStrategy 检测策略
 */
class CheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        // ==================== 输入 Key ====================
        const val KEY_CHECK_URL = "check_url"
        const val KEY_NOTIFY_ENABLED = "notify_enabled"
        const val KEY_NOTIFY_CHANNEL_ID = "notify_channel_id"
        const val KEY_NOTIFY_CHANNEL_NAME = "notify_channel_name"
        const val KEY_NOTIFY_TITLE = "notify_title"
        const val KEY_NOTIFY_TEXT_PREFIX = "notify_text_prefix"
        const val KEY_TIME_WINDOW_START_HOUR = "time_window_start_hour"
        const val KEY_TIME_WINDOW_END_HOUR = "time_window_end_hour"
        const val KEY_UPDATE_MODE = "update_mode"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_SAVE_PATH = "save_path"

        // ==================== 输出 Key ====================
        const val OUTPUT_HAS_NEW_VERSION = "has_new_version"
        const val OUTPUT_VERSION_NAME = "version_name"
        const val OUTPUT_VERSION_CODE = "version_code"
        const val OUTPUT_DOWNLOAD_URL = "download_url"
        const val OUTPUT_CHANGE_LOG = "change_log"
        const val OUTPUT_FORCE_UPDATE = "force_update"
        const val OUTPUT_FILE_MD5 = "file_md5"
        const val OUTPUT_FILE_SIZE = "file_size"

        // ==================== 默认值 ====================
        private const val DEFAULT_NOTIFY_CHANNEL_ID = "upgrade_check"
        private const val DEFAULT_NOTIFY_CHANNEL_NAME = "版本更新"
        private const val DEFAULT_NOTIFY_TITLE = "发现新版本"
        private const val DEFAULT_NOTIFY_TEXT_PREFIX = "版本"
        private const val NOTIFICATION_ID = 0x1001

        fun serializeMode(mode: UpdateMode): String = mode.name

        fun deserializeMode(value: String?): UpdateMode {
            return try {
                value?.let { UpdateMode.valueOf(it) } ?: UpdateMode.NOTIFY_ONLY
            } catch (_: IllegalArgumentException) {
                UpdateMode.NOTIFY_ONLY
            }
        }
    }

    override suspend fun doWork(): Result {
        val checkUrl = inputData.getString(KEY_CHECK_URL)
            ?: return Result.failure()
        val notifyEnabled = inputData.getBoolean(KEY_NOTIFY_ENABLED, false)
        val timeWindowStart = inputData.getInt(KEY_TIME_WINDOW_START_HOUR, -1)
        val timeWindowEnd = inputData.getInt(KEY_TIME_WINDOW_END_HOUR, -1)
        val updateMode = deserializeMode(inputData.getString(KEY_UPDATE_MODE))

        // 校验时间段（仅定时调度场景使用）
        if (timeWindowStart >= 0 && timeWindowEnd >= 0) {
            if (!isCurrentHourInWindow(timeWindowStart, timeWindowEnd)) {
                return Result.success(workDataOf("skipped" to true))
            }
        }

        // 获取检测策略并执行版本检查
        val strategy = StrategyHolder.getCheck()
        val result = strategy.check(checkUrl)

        val info = result.getOrNull() ?: return Result.retry()
        if (!info.hasNewVersion) {
            return Result.success(workDataOf(OUTPUT_HAS_NEW_VERSION to false))
        }

        // 通知（NOTIFY_ONLY 和 CONFIRM_DOWNLOAD 均显示通知）
        if (notifyEnabled && updateMode != UpdateMode.AUTO_DOWNLOAD) {
            showUpdateNotification(info)
        }

        return Result.success(buildNewVersionOutput(info))
    }

    // ==================== 时间窗口 ====================

    private fun isCurrentHourInWindow(startHour: Int, endHour: Int): Boolean {
        val currentHour = java.util.Calendar.getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)
        return if (startHour <= endHour) {
            currentHour in startHour..endHour
        } else {
            currentHour !in (endHour + 1)..<startHour
        }
    }

    // ==================== 通知 ====================

    private fun showUpdateNotification(info: UpgradeInfo) {
        val channelId = inputData.getString(KEY_NOTIFY_CHANNEL_ID) ?: DEFAULT_NOTIFY_CHANNEL_ID
        val channelName = inputData.getString(KEY_NOTIFY_CHANNEL_NAME) ?: DEFAULT_NOTIFY_CHANNEL_NAME
        val title = inputData.getString(KEY_NOTIFY_TITLE) ?: DEFAULT_NOTIFY_TITLE
        val textPrefix = inputData.getString(KEY_NOTIFY_TEXT_PREFIX) ?: DEFAULT_NOTIFY_TEXT_PREFIX

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "版本更新通知" }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("$textPrefix ${info.versionName}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$textPrefix ${info.versionName}\n${info.changeLog}")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // ==================== 输出构建 ====================

    private fun buildNewVersionOutput(info: UpgradeInfo): androidx.work.Data {
        return workDataOf(
            OUTPUT_HAS_NEW_VERSION to true,
            OUTPUT_VERSION_NAME to info.versionName,
            OUTPUT_VERSION_CODE to info.versionCode,
            OUTPUT_DOWNLOAD_URL to info.downloadUrl,
            OUTPUT_CHANGE_LOG to info.changeLog,
            OUTPUT_FORCE_UPDATE to info.forceUpdate,
            OUTPUT_FILE_MD5 to (info.fileMd5 ?: ""),
            OUTPUT_FILE_SIZE to info.fileSize,
        )
    }
}
