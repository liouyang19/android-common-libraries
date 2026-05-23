package com.taisau.android.common.upgrade.scheduler

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager 调度器，用于管理版本更新的定时检查任务。
 *
 * 使用方式：
 * ```kotlin
 * val scheduler = UpgradeScheduler(context)
 * scheduler.schedule(
 *     checkUrl = "https://api.example.com/check",
 *     config = UpgradeScheduleConfig.wifiOnly(),
 * )
 * ```
 */
class UpgradeScheduler(private val context: Context) {

    companion object {
        /** WorkManager 定时任务的唯一名称 */
        private const val UNIQUE_WORK_NAME = "upgrade_periodic_check"

        /** 最小定期间隔（WorkManager 要求至少 15 分钟） */
        private const val MIN_INTERVAL_MINUTES = 15L
    }

    /**
     * 调度定期的版本更新检查。
     *
     * @param checkUrl 版本检查接口 URL
     * @param config 调度配置（间隔、网络、充电等约束）
     * @param notifyEnabled 是否在发现新版本时发送通知
     * @param notifyTitle 通知标题
     * @param notifyTextPrefix 通知文本前缀
     */
    fun schedule(
        checkUrl: String,
        config: UpgradeScheduleConfig = UpgradeScheduleConfig(),
        notifyEnabled: Boolean = false,
        notifyTitle: String? = null,
        notifyTextPrefix: String? = null,
    ) {
        val inputData = buildInputData(
            checkUrl = checkUrl,
            notifyEnabled = notifyEnabled,
            notifyTitle = notifyTitle,
            notifyTextPrefix = notifyTextPrefix,
            timeWindowStartHour = config.timeWindowStartHour,
            timeWindowEndHour = config.timeWindowEndHour,
            updateMode = config.updateMode,
        )

        // 若配置了时间段，计算初始延迟以对齐窗口起始时间
        val initialDelayMinutes = if (config.timeWindowStartHour != null) {
            config.calculateDelayToWindowStart()
        } else {
            config.initialDelayHours * 60
        }

        val workRequest = PeriodicWorkRequestBuilder<CheckWorker>(
            config.intervalHours, TimeUnit.HOURS,
        )
            .setConstraints(config.toWorkConstraints())
            .setInputData(inputData)
            .addTag(UNIQUE_WORK_NAME)
            .apply {
                if (initialDelayMinutes > 0) {
                    setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                }
            }
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
    }

    /**
     * 取消定期的版本更新检查。
     */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /**
     * 查询定时检查是否已调度。
     */
    fun isScheduled(): Boolean {
        val workManager = WorkManager.getInstance(context)
        val workInfos = workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME)
            .get()
        return workInfos.any { info ->
            !info.state.isFinished
        }
    }

    /**
     * 构建 Worker 的输入数据。
     */
    internal fun buildInputData(
        checkUrl: String,
        notifyEnabled: Boolean,
        notifyTitle: String?,
        notifyTextPrefix: String?,
        timeWindowStartHour: Int? = null,
        timeWindowEndHour: Int? = null,
        updateMode: UpdateMode = UpdateMode.NOTIFY_ONLY,
    ): Data {
        val dataBuilder = Data.Builder()
            .putString(CheckWorker.KEY_CHECK_URL, checkUrl)
            .putBoolean(CheckWorker.KEY_NOTIFY_ENABLED, notifyEnabled)
            .putString(CheckWorker.KEY_UPDATE_MODE, CheckWorker.serializeMode(updateMode))

        if (notifyTitle != null) {
            dataBuilder.putString(CheckWorker.KEY_NOTIFY_TITLE, notifyTitle)
        }
        if (notifyTextPrefix != null) {
            dataBuilder.putString(CheckWorker.KEY_NOTIFY_TEXT_PREFIX, notifyTextPrefix)
        }
        if (timeWindowStartHour != null) {
            dataBuilder.putInt(CheckWorker.KEY_TIME_WINDOW_START_HOUR, timeWindowStartHour)
        }
        if (timeWindowEndHour != null) {
            dataBuilder.putInt(CheckWorker.KEY_TIME_WINDOW_END_HOUR, timeWindowEndHour)
        }
        return dataBuilder.build()
    }
}
