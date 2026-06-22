package com.taisau.android.common.upgrade.scheduler

import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.Calendar

/**
 * 升级检查的调度配置，通过 WorkManager 控制何时触发版本检测。
 *
 * @property intervalHours 定期间隔（小时），默认 24 小时
 * @property requiredNetworkType 所需的网络类型，默认 [NetworkType.CONNECTED]
 * @property requiresCharging 是否仅在充电时检查，默认 false
 * @property requiresDeviceIdle 是否仅在设备空闲时检查，默认 false
 * @property initialDelayHours 首次检查的延迟时间（小时），默认 0 表示立即
 * @property requiresBatteryNotLow 是否要求电量不低，默认 true
 * @property requiresStorageNotLow 是否要求存储空间不低，默认 true
 * @property updateMode 发现新版本后的更新模式，默认 [UpdateMode.NOTIFY_ONLY]
 * @property timeWindowStartHour 允许检测的时间段起始小时（0-23），null 表示不限制
 * @property timeWindowEndHour 允许检测的时间段结束小时（0-23），null 表示不限制
 *
 * 时间段示例：
 * - `timeWindowStartHour=2, timeWindowEndHour=5` → 凌晨 2:00~5:59 之间可检测
 * - `timeWindowStartHour=22, timeWindowEndHour=2` → 22:00~次日 1:59（跨午夜）
 * - 两者均为 null → 不限时间段
 */
data class UpgradeScheduleConfig(
    val intervalHours: Long = 24,
    val requiredNetworkType: NetworkType = NetworkType.CONNECTED,
    val requiresCharging: Boolean = false,
    val requiresDeviceIdle: Boolean = false,
    val initialDelayHours: Long = 0,
    val requiresBatteryNotLow: Boolean = true,
    val requiresStorageNotLow: Boolean = true,
    val updateMode: UpdateMode = UpdateMode.NOTIFY_ONLY,
    val timeWindowStartHour: Int? = null,
    val timeWindowEndHour: Int? = null,
) {
    init {
        require(intervalHours >= 1) { "intervalHours must be >= 1" }
        timeWindowStartHour?.let {
            require(it in 0..23) { "timeWindowStartHour must be 0-23" }
        }
        timeWindowEndHour?.let {
            require(it in 0..23) { "timeWindowEndHour must be 0-23" }
        }
    }

    /** 将调度配置转换为 WorkManager [Constraints] */
    fun toWorkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(requiredNetworkType)
        .setRequiresCharging(requiresCharging)
        .setRequiresDeviceIdle(requiresDeviceIdle)
        .setRequiresBatteryNotLow(requiresBatteryNotLow)
        .setRequiresStorageNotLow(requiresStorageNotLow)
        .build()

    /**
     * 判断当前时间是否在允许检测的时间段内。
     *
     * 如果 [timeWindowStartHour] 或 [timeWindowEndHour] 为 null，视为不限时间段（始终返回 true）。
     */
    fun isInTimeWindow(): Boolean {
        val start = timeWindowStartHour ?: return true
        val end = timeWindowEndHour ?: return true
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (start <= end) {
            // 普通区间：如 2~5 → 2,3,4,5
            currentHour in start..end
        } else {
            // 跨午夜区间：如 22~2 → 22,23,0,1
            currentHour >= start || currentHour <= end
        }
    }

    /**
     * 计算距离下一次时间窗口起始点的小时数（用于初始延迟对齐）。
     * 如果未设置时间段则返回 0（无需对齐）。
     */
    fun calculateDelayToWindowStart(): Long {
        val start = timeWindowStartHour ?: return 0L
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        val nowTotalMinutes = currentHour * 60 + currentMinute
        val startTotalMinutes = start * 60

        return if (nowTotalMinutes < startTotalMinutes) {
            // 今天还没到窗口时间
            (startTotalMinutes - nowTotalMinutes).toLong()
        } else {
            // 今天窗口已过，明天再说
            (24 * 60 - nowTotalMinutes + startTotalMinutes).toLong()
        }
    }

    companion object {
        /** 仅在 WiFi 下检查更新 */
        fun wifiOnly() = UpgradeScheduleConfig(
            requiredNetworkType = NetworkType.UNMETERED,
        )

        /** 仅在 WiFi + 充电时检查更新 */
        fun wifiAndCharging() = UpgradeScheduleConfig(
            requiredNetworkType = NetworkType.UNMETERED,
            requiresCharging = true,
        )

        /** 仅在空闲 + WiFi 时检查更新 */
        fun idleAndWifi() = UpgradeScheduleConfig(
            requiredNetworkType = NetworkType.UNMETERED,
            requiresDeviceIdle = true,
        )

        /** 最低限制：有网络即可 */
        fun anyNetwork() = UpgradeScheduleConfig(
            requiredNetworkType = NetworkType.CONNECTED,
            requiresBatteryNotLow = false,
            requiresStorageNotLow = false,
        )

        /**
         * 仅在凌晨时段检测（默认 2:00~5:00）。
         * @param startHour 起始小时（默认 2）
         * @param endHour 结束小时（默认 5）
         */
        fun overnightWindow(
            startHour: Int = 2,
            endHour: Int = 5,
        ) = UpgradeScheduleConfig(
            timeWindowStartHour = startHour,
            timeWindowEndHour = endHour,
        )

        /**
         * 仅在深夜时段检测（23:00~次日 6:00）。
         */
        fun lateNightWindow() = UpgradeScheduleConfig(
            timeWindowStartHour = 23,
            timeWindowEndHour = 6,
        )
    }
}
