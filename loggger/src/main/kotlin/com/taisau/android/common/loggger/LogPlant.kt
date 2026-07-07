@file:Suppress("unused")

package com.taisau.android.common.loggger

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Plant（日志树）抽象基类 —— Timber.Tree 模式。
 *
 * 每个 Plant 代表一种日志输出目标。通过 [Loggger.plant] 注册后，
 * 所有日志会同时分发到每一个已注册的 Plant。
 *
 * ### 内置 Plant 实现
 * - [LogcatPlant] — 输出到 Android Logcat
 * - [DatabasePlant] — 持久化到 Room 数据库 / ContentProvider
 * - [FilePlant] — 写入日志文件（支持按大小旋转）
 *
 * ### 自定义 Plant
 * ```
 * class CrashReportPlant : LogPlant() {
 *     override fun performLog(entry: LogEntry) {
 *         if (entry.level >= LogLevel.ERROR) {
 *             CrashReporter.log(entry.message, entry.throwable)
 *         }
 *     }
 * }
 * ```
 */
abstract class LogPlant {

    /**
     * 最低日志级别，低于此级别的日志将被 [isLoggable] 过滤掉。
     * 默认 [LogLevel.VERBOSE]（全部输出）。
     */
    @Volatile
    var minLevel: LogLevel = LogLevel.VERBOSE

    // ── 子类重写 ──

    /**
     * 子类实现此方法以处理日志。
     *
     * **注意**：此方法仅在 [isLoggable] 返回 true 时才会被调用，
     * 因此子类无需重复检查级别。
     */
    protected abstract fun performLog(entry: LogEntry)

    /**
     * 插入一条分割线（由 [Loggger.divider] 触发）。
     *
     * [LogcatPlant] 会输出一条自定义分割线到 Logcat，
     * 其他 Plant 默认无操作。
     */
    protected open fun onDivider() { }

    /**
     * 日志级别过滤器。返回 false 则跳过此条日志。
     *
     * 默认实现仅检查 [minLevel]。子类可重写以实现更精细的过滤
     * （如按 tag 白名单过滤）。
     */
    protected open fun isLoggable(priority: Int): Boolean {
        return priority >= minLevel.priority
    }

    // ── 内部方法（由 Loggger 调用） ──

    /**
     * 接收一条日志并写入此 Plant。
     * 内部完成过滤检查 + [LogEntry] 构建后交给 [performLog]。
     */
    internal open fun write(
        level: LogLevel,
        tag: String?,
        message: String,
        throwable: Throwable?
    ) {
        if (!isLoggable(level.priority)) return
        performLog(
            LogEntry(
                id = generateId(),
                level = level,
                tag = tag ?: "Loggger",
                message = message,
                throwable = throwable?.toString(),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * 写入一个预构建的 [LogEntry]（供 [Loggger.log] 使用）。
     * 内部过滤检查后交给 [performLog]。
     */
    internal fun writeEntry(entry: LogEntry) {
        if (!isLoggable(entry.level.priority)) return
        performLog(entry)
    }

    /**
     * 插入分割线（供 [Loggger.divider] 使用）。
     * 委托给子类的 [onDivider] 实现。
     */
    internal fun invokeDivider() {
        onDivider()
    }

    // ── ID 生成 ──

    private val sequence = AtomicLong(0)

    private fun generateId(): Long {
        val now = System.currentTimeMillis()
        val seq = sequence.incrementAndGet()
        // 时间戳（毫秒）高 44 位 + 序列号低 20 位 => 单进程内唯一且递增
        return (now shl 20) or (seq and 0xFFFFF)
    }

    // ── 森林节点（内部用于 Loggger.tag().d() 链式调用） ──

    /**
     * 森林植物 —— 不实际处理日志，而是将日志分发给森林中的所有 Plant。
     */
    internal class Forest : LogPlant() {
        private val plants: CopyOnWriteArrayList<LogPlant>
            get() = Loggger.plants

        override fun performLog(entry: LogEntry) {
            // 此方法不应被直接调用
            for (plant in plants) {
                if (plant !== this) {
                    plant.performLog(entry)
                }
            }
        }

        override fun write(level: LogLevel, tag: String?, message: String, throwable: Throwable?) {
            for (plant in plants) {
                if (plant !== this) {
                    plant.write(level, tag, message, throwable)
                }
            }
        }

        override fun isLoggable(priority: Int): Boolean = true // 森林不做过滤

        override fun onDivider() {
            for (plant in plants) {
                if (plant !== this) {
                    plant.onDivider()
                }
            }
        }
    }
}
