@file:Suppress("unused")

package com.taisau.android.common.loggger

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Loggger 门面 —— Timber 风格的 Plant/Forest 日志系统。
 *
 * ### 快速开始
 * ```
 * // 在 Application.onCreate 中注册 Plant
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Loggger.plant(LogcatPlant())
 *         Loggger.plant(DatabasePlant(this))
 *         Loggger.plant(FilePlant(this))
 *     }
 * }
 *
 * // 任意位置使用
 * Loggger.d("MainActivity", "onCreate")
 * Loggger.i("Network", "HTTP 200")
 * Loggger.e("Crash", "OOM", exception)
 * ```
 *
 * ### 常见组合
 * ```
 * // 仅 Logcat（开发阶段）
 * Loggger.plant(LogcatPlant())
 *
 * // Logcat + 入库（默认行为）
 * Loggger.plant(LogcatPlant())
 * Loggger.plant(DatabasePlant(this))
 *
 * // Logcat + 入库 + 写文件
 * Loggger.plant(LogcatPlant())
 * Loggger.plant(DatabasePlant(this))
 * Loggger.plant(FilePlant(this))
 *
 * // 仅写文件（生产环境静默记录）
 * Loggger.plant(FilePlant(this))
 * ```
 *
 * ### 链式 Tag（模仿 Timber.tag()）
 * ```
 * Loggger.tag("Auth").d("登录成功")
 * Loggger.tag("Network").e("请求失败", exception)
 * ```
 *
 * ### 分割线
 * ```
 * // 自动分割线（每 5 条日志）
 * Loggger.plant(LogcatPlant(divider = "─".repeat(30), dividerInterval = 5))
 *
 * // 手动分割线
 * Loggger.divider()
 * ```
 *
 * ### 跨进程读取
 * ```
 * val cursor = context.contentResolver.query(
 *     LogProviderContract.CONTENT_URI, null, null, null, null
 * )
 * ```
 */
object Loggger {

    /** 已注册的 Plant 列表（线程安全）。 */
    internal val plants = CopyOnWriteArrayList<LogPlant>()

    /** 森林节点（用于 [tag] 链式调用）。 */
    private val forest = LogPlant.Forest()

    // ── Plant 管理 ──

    /**
     * 注册一个 Plant。所有后续日志将同时分发给此 Plant。
     *
     * @throws IllegalArgumentException 如果已存在相同的 Plant 实例
     */
    @JvmStatic
    fun plant(plant: LogPlant) {
        require(plant !in plants) { "Plant already planted: $plant" }
        plants.add(plant)
    }

    /** 注销指定的 Plant。 */
    @JvmStatic
    fun uproot(plant: LogPlant) {
        plants.remove(plant)
    }

    /** 注销所有 Plant。 */
    @JvmStatic
    fun uprootAll() {
        plants.clear()
    }

    /** 获取当前已注册的所有 Plant（防御性拷贝）。 */
    @JvmStatic
    fun planted(): List<LogPlant> = plants.toList()

    /** 当前是否有任何已注册的 Plant。 */
    val hasPlants: Boolean get() = plants.isNotEmpty()

    // ── Timber.tag() 链式支持 ──

    /**
     * 设置下次日志调用的 Tag（ThreadLocal，一次有效）。
     *
     * 返回 [LogPlant] 以支持链式调用：
     * ```
     * Loggger.tag("Auth").d("登录成功")
     * ```
     */
    @JvmStatic
    fun tag(tag: String): LogPlant {
        _pendingTag.set(tag)
        return forest
    }

    private val _pendingTag = ThreadLocal<String>()

    /** 消费挂起的 Tag 并返回。 */
    private fun consumeTag(): String? {
        val tag = _pendingTag.get()
        _pendingTag.remove()
        return tag
    }

    // ── 便捷初始化（快速上手） ──

    /**
     * 便捷初始化 —— 一次注册 [LogcatPlant] + 可选 [DatabasePlant] / [FilePlant]。
     *
     * 等同于：
     * ```
     * Loggger.plant(LogcatPlant())
     * if (database) Loggger.plant(DatabasePlant(context))
     * if (file) Loggger.plant(FilePlant(context, ...))
     * ```
     *
     * @param cleanupStrategy 文件清除策略（默认 [CleanupStrategy.DEFAULT]）
     * @param backupStrategy 文件备份策略（默认 [BackupStrategy.NO_BACKUP]）
     */
    @JvmStatic
    fun init(
        context: Context,
        database: Boolean = false,
        file: Boolean = false,
        fileDir: String? = null,
        fileMaxSize: Long = 5 * 1024 * 1024,
        cleanupStrategy: CleanupStrategy = CleanupStrategy.DEFAULT,
        backupStrategy: BackupStrategy = BackupStrategy.NO_BACKUP
    ) {
        plant(LogcatPlant())
        if (database) plant(DatabasePlant(context))
        if (file) {
            plant(
                FilePlant(
                    context = context,
                    logDir = if (fileDir != null) java.io.File(fileDir) else null,
                    maxFileSize = fileMaxSize,
                    cleanupStrategy = cleanupStrategy,
                    backupStrategy = backupStrategy
                )
            )
        }
    }

    // ── 日志方法 ──

    /** 获取调用者类名作为默认 Tag。 */
    private fun autoTag(): String? {
        val stack = Throwable().stackTrace
        // 向上查找第一个非 Loggger 的调用者
        for (i in 2 until stack.size) {
            val className = stack[i].className
            if (!className.startsWith("com.taisau.android.common.loggger.LogPlant") &&
                !className.startsWith("com.taisau.android.common.loggger.Loggger")) {
                return className.substringAfterLast('.').substringBefore('$')
            }
        }
        return null
    }

    @JvmStatic
    fun v(tag: String?, message: String, throwable: Throwable? = null) {
        val t = tag ?: consumeTag() ?: autoTag()
        for (p in plants) p.write(LogLevel.VERBOSE, t, message, throwable)
    }

    @JvmStatic
    fun d(tag: String?, message: String, throwable: Throwable? = null) {
        val t = tag ?: consumeTag() ?: autoTag()
        for (p in plants) p.write(LogLevel.DEBUG, t, message, throwable)
    }

    @JvmStatic
    fun i(tag: String?, message: String, throwable: Throwable? = null) {
        val t = tag ?: consumeTag() ?: autoTag()
        for (p in plants) p.write(LogLevel.INFO, t, message, throwable)
    }

    @JvmStatic
    fun w(tag: String?, message: String, throwable: Throwable? = null) {
        val t = tag ?: consumeTag() ?: autoTag()
        for (p in plants) p.write(LogLevel.WARN, t, message, throwable)
    }

    @JvmStatic
    fun e(tag: String?, message: String, throwable: Throwable? = null) {
        val t = tag ?: consumeTag() ?: autoTag()
        for (p in plants) p.write(LogLevel.ERROR, t, message, throwable)
    }

    @JvmStatic
    fun log(entry: LogEntry) {
        for (p in plants) p.writeEntry(entry)
    }

    /**
     * 手动插入一条分割线。
     *
     * 会调用所有 Plant 的 [LogPlant.onDivider]。
     * [LogcatPlant] 会输出配置的 [LogcatPlant.divider] 文本到 Logcat。
     *
     * ```
     * Loggger.d("Main", "第一段")
     * Loggger.divider()
     * Loggger.d("Main", "第二段")
     * ```
     */
    @JvmStatic
    fun divider() {
        for (p in plants) p.invokeDivider()
    }

    // ── 无 Tag 重载（自动推断 Tag） ──

    @JvmStatic
    fun v(message: String, throwable: Throwable? = null): Unit = v(null, message, throwable)

    @JvmStatic
    fun d(message: String, throwable: Throwable? = null): Unit = d(null, message, throwable)

    @JvmStatic
    fun i(message: String, throwable: Throwable? = null): Unit = i(null, message, throwable)

    @JvmStatic
    fun w(message: String, throwable: Throwable? = null): Unit = w(null, message, throwable)

    @JvmStatic
    fun e(message: String, throwable: Throwable? = null): Unit = e(null, message, throwable)
}
