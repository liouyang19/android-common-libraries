package com.taisau.android.common.upgrade.scheduler

import com.taisau.android.common.upgrade.strategy.CheckStrategy
import com.taisau.android.common.upgrade.strategy.DownloadStrategy
import com.taisau.android.common.upgrade.strategy.InstallStrategy
import com.taisau.android.common.upgrade.strategy.UpgradeStrategy

/**
 * 策略持有者（内部单例）。
 *
 * 由于 WorkManager 通过反射创建 [CoroutineWorker]，Worker 无法通过构造器接收依赖注入。
 * [StrategyHolder] 作为静态桥接层，让 [CheckWorker]、[DownloadWorker]、[InstallWorker]
 * 能够获取到用户在 [UpgradeManager.Builder] 中设置的策略实现。
 *
 * 使用方式：
 * 1. [UpgradeManager] 构建时调用 [registerAll] 注册策略
 * 2. Worker 在 [androidx.work.CoroutineWorker.doWork] 中通过 getter 获取策略
 * 3. 页面销毁时可调用 [clear] 清理引用
 */
internal object StrategyHolder {

    @Volatile
    var checkStrategy: CheckStrategy? = null

    @Volatile
    var downloadStrategy: DownloadStrategy? = null

    @Volatile
    var installStrategy: InstallStrategy? = null

    /**
     * 同时注册三个子策略（当使用者分别实现了不同接口时使用）。
     */
    fun register(
        check: CheckStrategy,
        download: DownloadStrategy,
        install: InstallStrategy,
    ) {
        checkStrategy = check
        downloadStrategy = download
        installStrategy = install
    }

    /**
     * 通过统一的 [UpgradeStrategy] 注册全部三个子策略。
     */
    fun registerAll(strategy: UpgradeStrategy) {
        checkStrategy = strategy
        downloadStrategy = strategy
        installStrategy = strategy
    }

    /**
     * 获取 [CheckStrategy]。
     *
     * @throws IllegalStateException 如果策略尚未注册
     */
    fun getCheck(): CheckStrategy {
        return checkStrategy ?: throw IllegalStateException(
            "CheckStrategy not registered. Call UpgradeManager.Builder.build() first."
        )
    }

    /**
     * 获取 [DownloadStrategy]。
     *
     * @throws IllegalStateException 如果策略尚未注册
     */
    fun getDownload(): DownloadStrategy {
        return downloadStrategy ?: throw IllegalStateException(
            "DownloadStrategy not registered. Call UpgradeManager.Builder.build() first."
        )
    }

    /**
     * 获取 [InstallStrategy]。
     *
     * @throws IllegalStateException 如果策略尚未注册
     */
    fun getInstall(): InstallStrategy {
        return installStrategy ?: throw IllegalStateException(
            "InstallStrategy not registered. Call UpgradeManager.Builder.build() first."
        )
    }

    /** 清理所有策略引用。 */
    fun clear() {
        checkStrategy = null
        downloadStrategy = null
        installStrategy = null
    }
}
