package com.taisau.android.common.upgrade

import android.content.Context
import com.taisau.android.common.upgrade.scheduler.UpgradeScheduleConfig
import com.taisau.android.common.upgrade.strategy.CheckStrategy
import com.taisau.android.common.upgrade.strategy.DownloadStrategy
import com.taisau.android.common.upgrade.strategy.InstallStrategy
import com.taisau.android.common.upgrade.strategy.UpgradeStrategy
import kotlinx.coroutines.flow.StateFlow

/**
 * App 版本更新管理器接口。
 *
 * 采用 Builder 模式构建（通过 [Builder]），核心操作均通过 WorkManager Worker
 * 在后台执行，并通过 [state] 实时暴露当前升级步骤。
 *
 * 核心能力：
 * - [check] 检测新版本
 * - [download] 下载 APK
 * - [install] 安装 APK
 * - [cancel] 取消当前正在执行的 Worker 操作
 * - [scheduleUpdateCheck] 配置 WorkManager 定时检查
 *
 * ⚠️ 所有核心操作均为无参、无返回的挂起函数。
 * 调用方应通过 [state] collect 驱动 UI，无需关注返回值。
 *
 * @see UpgradeManagerImpl 默认实现
 */
interface UpgradeManager {

    // ==================== 状态 ====================

    /**
     * 当前升级状态。
     *
     * 调用 [check]、[download]、[install] 时自动更新。
     * 在 [UpdateMode.AUTO_DOWNLOAD] 模式下，状态会自动推进（如检测完成→自动下载→自动安装）。
     */
    val state: StateFlow<UpgradeState>

    // ==================== 核心操作（无参、无返回、纯状态驱动） ====================

    /**
     * 检测新版本。
     *
     * 创建 [CheckWorker] 的 [androidx.work.OneTimeWorkRequest] 并等待结果。
     * 状态流转：`Idle → Checking → NewVersionReady / NoNewVersion / CheckFailed`
     *
     * 若 [Builder.setNotifyEnabled] 开启，发现新版本时会在通知栏显示通知。
     * 在 [UpdateMode.AUTO_DOWNLOAD] 模式下，检测到新版本后会自动调用 [download]。
     */
    suspend fun check()

    /**
     * 下载 APK。
     *
     * 从当前 [UpgradeState.NewVersionReady] 状态中读取版本信息，创建
     * [DownloadWorker] 的 [androidx.work.OneTimeWorkRequest] 并等待结果。
     * 状态流转：`NewVersionReady → Downloading → DownloadCompleted / DownloadFailed`
     *
     * 下载完成后，内部会启动轮询监听 [DownloadManager] 进度。
     * 当下载成功时自动设置 [UpgradeState.DownloadCompleted]，并（在 auto 模式下）自动调用 [install]。
     *
     * 若 [Builder.setNotifyEnabled] 开启，下载完成时会在通知栏显示通知。
     *
     * @throws IllegalStateException 如果当前状态不是 [UpgradeState.NewVersionReady]
     */
    suspend fun download()

    /**
     * 安装 APK。
     *
     * 从当前 [UpgradeState.DownloadCompleted] 状态中读取 APK 路径，创建
     * [InstallWorker] 的 [androidx.work.OneTimeWorkRequest] 并等待结果。
     * 状态流转：`DownloadCompleted → Installing → Installed / InstallFailed`
     *
     * @throws IllegalStateException 如果当前状态不是 [UpgradeState.DownloadCompleted]
     */
    suspend fun install()

    /**
     * 取消当前正在执行的升级操作。
     *
     * 具体行为：
     * - 取消正在运行的 WorkManager Worker（CheckWorker / DownloadWorker / InstallWorker）
     * - 取消下载进度轮询
     * - 如果正在下载，通过 [DownloadManager] 取消下载任务
     * - 重置状态为 [UpgradeState.Idle]
     */
    fun cancel()

    // ==================== 定时调度 ====================

    /** 启动 WorkManager 定时版本检查。需先通过 [Builder.setScheduleConfig] 配置。 */
    fun scheduleUpdateCheck()

    /** 取消 WorkManager 定时版本检查。 */
    fun cancelScheduledCheck()

    /** 查询定时版本检查是否已调度。 */
    fun isScheduledCheck(): Boolean

    companion object {

        fun create(context: Context,builder: Builder): UpgradeManager{
            return UpgradeManagerImpl.create(context,builder)
        }
    }

    // ==================== Builder ====================

    /**
     * [UpgradeManager] 的 Builder。
     *
     * 支持注入完整 [UpgradeStrategy] 或分别注入 [CheckStrategy]、[DownloadStrategy]、
     * [InstallStrategy] 的任意组合。不注入时使用默认实现。
     *
     * 使用示例：
     * ```kotlin
     * val manager = UpgradeManager.Builder(context)
     *     .setCheckUrl("https://api.example.com/check")
     *     .setNotifyEnabled(true)                // 开启通知栏通知
     *     .setSaveDirectory("/sdcard/Download")   // 指定 APK 存放目录（可选）
     *     .setScheduleConfig(UpgradeScheduleConfig.wifiOnly())
     *     .build()
     * ```
     */
    class Builder(private val context: Context) {
        private var checkUrl: String = ""
        private var saveDirectory: String? = null
        private var scheduleConfig: UpgradeScheduleConfig? = null
        private var notifyEnabled: Boolean = false
        private var scheduleNotifyEnabled: Boolean = false
        private var scheduleNotifyTitle: String? = null
        private var scheduleNotifyTextPrefix: String? = null

        // 策略注入
        private var checkStrategy: CheckStrategy? = null
        private var downloadStrategy: DownloadStrategy? = null
        private var installStrategy: InstallStrategy? = null

        /** 设置版本检查接口 URL。必须设置。 */
        fun setCheckUrl(url: String) = apply { checkUrl = url }

        /**
         * 设置 APK 下载的存放目录。
         *
         * 传入空字符串或 null 时，自动使用 `cacheDir/upgrade/`。
         * 目录不存在时会自动创建。
         */
        fun setSaveDirectory(dir: String?) = apply { saveDirectory = dir?.ifBlank { null } }

        /**
         * 是否在通知栏显示升级通知（检测到新版本、下载完成等）。
         * 默认为 false。设为 true 时会使用默认通知渠道和文案。
         */
        fun setNotifyEnabled(enabled: Boolean) = apply { notifyEnabled = enabled }

        /** 统一注入完整的升级策略。同时设置三个子接口。 */
        fun setStrategy(strategy: UpgradeStrategy) = apply {
            this.checkStrategy = strategy
            this.downloadStrategy = strategy
            this.installStrategy = strategy
        }

        /** 单独注入版本检测策略。不调用时默认使用 [DefaultCheckStrategy]。 */
        fun setCheckStrategy(strategy: CheckStrategy) = apply {
            this.checkStrategy = strategy
        }

        /** 单独注入下载策略。不调用时默认使用 [DefaultDownloadStrategy]。 */
        fun setDownloadStrategy(strategy: DownloadStrategy) = apply {
            this.downloadStrategy = strategy
        }

        /** 单独注入安装策略。不调用时默认使用 [DefaultInstallStrategy]。 */
        fun setInstallStrategy(strategy: InstallStrategy) = apply {
            this.installStrategy = strategy
        }

        /**
         * 配置 WorkManager 定时检查更新的调度参数。
         * @param config 调度配置，传入 null 表示不启用定时检查。
         */
        fun setScheduleConfig(config: UpgradeScheduleConfig?) = apply {
            this.scheduleConfig = config
        }

        /** 设置在定时检查发现新版本时，是否发送系统通知。仅当 [setScheduleConfig] 设置了非 null 值时生效。 */
        fun setScheduleNotifyEnabled(enabled: Boolean) = apply {
            this.scheduleNotifyEnabled = enabled
        }

        /** 设置定时检查发现新版本时的通知标题。 */
        fun setScheduleNotifyTitle(title: String) = apply {
            this.scheduleNotifyTitle = title
        }

        /** 设置定时检查发现新版本时的通知文本前缀。 */
        fun setScheduleNotifyTextPrefix(prefix: String) = apply {
            this.scheduleNotifyTextPrefix = prefix
        }

        /** 构建 [UpgradeManager] 实例。 */
        fun build(): UpgradeManager {
            return UpgradeManagerImpl.create(context, this)
        }

        // 内部暴露给 UpgradeManagerImpl 的属性
        internal fun toConfig() = Config(
            checkUrl = checkUrl,
            saveDirectory = saveDirectory,
            scheduleConfig = scheduleConfig,
            notifyEnabled = notifyEnabled,
            scheduleNotifyEnabled = scheduleNotifyEnabled,
            scheduleNotifyTitle = scheduleNotifyTitle,
            scheduleNotifyTextPrefix = scheduleNotifyTextPrefix,
        )

        internal fun getCheckStrategy(): CheckStrategy? = checkStrategy
        internal fun getDownloadStrategy(): DownloadStrategy? = downloadStrategy
        internal fun getInstallStrategy(): InstallStrategy? = installStrategy
    }

    /** 内部配置数据类。 */
    data class Config(
        val checkUrl: String,
        val saveDirectory: String? = null,
        val scheduleConfig: UpgradeScheduleConfig? = null,
        val notifyEnabled: Boolean = false,
        val scheduleNotifyEnabled: Boolean = false,
        val scheduleNotifyTitle: String? = null,
        val scheduleNotifyTextPrefix: String? = null,
    )
}
