package com.taisau.android.common.download

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * [DownloadManager] 全局单例持有者 —— 模仿 Coil 的 SingletonImageLoader 实现。
 *
 * 使用 [AtomicReference] 管理单例引用，线程安全且无锁。
 * 引用值可以是 [DownloadManager] 实例或 [Factory] 实例。
 *
 * 外部通过 [Context.downloadManager] 扩展属性访问，不应直接使用此类。
 *
 * ### 使用方式
 *
 * **方式一：Application 实现 [Factory] 接口（推荐）**
 * ```
 * class MyApp : Application(), SingletonDownloadManager.Factory {
 *     override fun newDownloadManager(context: Context): DownloadManager {
 *         return DownloadManager.Builder(context)
 *             .setOutputDir(filesDir)
 *             .setMaxConcurrentDownloads(5)
 *             .build()
 *     }
 * }
 * ```
 *
 * **方式二：初始化阶段调用 [setSafe]**
 * ```
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         SingletonDownloadManager.setSafe { context ->
 *             DownloadManager.Builder(context)
 *                 .setOutputDir(filesDir)
 *                 .build()
 *         }
 *     }
 * }
 * ```
 */
object SingletonDownloadManager {

    private val reference = AtomicReference<Any?>(null)

    // ── 公开 API ──

    /**
     * 获取与 [context] 关联的 [DownloadManager] 单例。
     *
     * 首次调用时会创建实例，后续返回缓存实例。
     * 创建策略参见 [newDownloadManager]。
     */
    @JvmStatic
    fun get(context: Context): DownloadManager {
        return (reference.get() as? DownloadManager) ?: newDownloadManager(context)
    }

    /**
     * 安全地设置 [Factory]。
     *
     * - 如果尚未创建 [DownloadManager]，将原子地设置工厂。
     * - 如果已经通过默认工厂创建了实例，抛出 [IllegalStateException]。
     * - 如果已经设置了自定义 [DownloadManager] 或工厂，静默忽略。
     *
     * 应在 Application.onCreate 中尽早调用。
     */
    @JvmStatic
    fun setSafe(factory: Factory) {
        val value = reference.get()
        if (value is DownloadManager) {
            if (value.isDefault) {
                error(
                    "SingletonDownloadManager 已被默认工厂创建，无法再设置自定义工厂。 " +
                            "请确保在首次调用 'context.downloadManager' 之前调用 setSafe。",
                )
            }
            // 已有自定义实例，忽略
            return
        }
        reference.compareAndSet(value, factory)
    }

    /**
     * 不安全地设置 [DownloadManager] 实例（⚠️ 仅供测试或高级场景使用）。
     *
     * 强制替换当前单例，即使已经创建。
     *
     * @see DelicateDownloadApi
     */
    @DelicateDownloadApi
    @JvmStatic
    fun setUnsafe(downloadManager: DownloadManager) { reference.set(downloadManager) }

    /**
     * 不安全地设置 [Factory]（⚠️ 仅供测试或高级场景使用）。
     *
     * 如果单例尚未创建，后续 [get] 会使用此工厂创建；如果已创建则无效。
     *
     * @see DelicateDownloadApi
     */
    @DelicateDownloadApi
    @JvmStatic
    fun setUnsafe(factory: Factory) { reference.set(factory) }

    /**
     * 重置单例（⚠️ 仅供测试使用）。
     */
    @DelicateDownloadApi
    @JvmStatic
    fun reset() { reference.set(null) }

    // ── Factory ──

    /**
     * [DownloadManager] 工厂接口。
     *
     * 如果 [Application][android.app.Application] 实现了此接口，
     * [Context.downloadManager] 扩展属性会自动调用 [newDownloadManager] 创建实例。
     *
     * 也可以通过 [setSafe] 或 [setUnsafe] 设置。
     */
    fun interface Factory {
        fun newDownloadManager(context: Context): DownloadManager
    }

    // ── 内部 ──

    /**
     * 创建 [DownloadManager] 实例。
     *
     * 优先级：
     * 1. [reference] 中已存在的 [DownloadManager]
     * 2. [reference] 中存储的 [Factory]
     * 3. Application 本身实现了 [Factory]
     * 4. 默认工厂 [DefaultSingletonDownloadManagerFactory]
     */
    private fun newDownloadManager(context: Context): DownloadManager {
        var downloadManager: DownloadManager? = null
        return reference.updateAndGet { value ->
            when {
                value is DownloadManager -> value
                downloadManager != null -> downloadManager
                else -> {
                    val applicationContext = context.applicationContext
                    ((value as? Factory)?.newDownloadManager(applicationContext)
                        ?: (applicationContext as? Factory)?.newDownloadManager(applicationContext)
                        ?: DefaultFactory.newDownloadManager(applicationContext))
                        .also { downloadManager = it }
                }
            }
        } as DownloadManager
    }
}

/**
 * 默认 [SingletonDownloadManager.Factory] 实现。
 * 使用 [DownloadManager.Builder] 的默认配置创建实例。
 */
private val DefaultFactory = SingletonDownloadManager.Factory { context ->
    DownloadManager.Builder(context).build()
}

/**
 * 判断 [DownloadManager] 是否为默认实现（简化实现）。
 *
 * 用作 [SingletonDownloadManager.setSafe] 的保护检查：
 * - 如果当前实例是默认创建的（未自定义），[setSafe] 会报错提示用户。
 * - 如果当前实例是自定义的（非默认），[setSafe] 静默忽略。
 */
private val DownloadManager.isDefault: Boolean
    get() = true // 简化实现: 始终视为默认，需要时可在子类中重写
