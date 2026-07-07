@file:Suppress("unused")

package com.taisau.android.common.loggger

import android.util.Log

/**
 * Logcat 输出 Plant —— 将日志输出到 Android Logcat，支持分割线和 ADB 动态开关。
 *
 * ### 基本用法
 * ```
 * Loggger.plant(LogcatPlant())
 * Loggger.d("MainActivity", "hello")
 * ```
 *
 * ### ADB 动态开关（无需重启 App）
 * ```
 * // 注册时指定系统属性名
 * Loggger.plant(LogcatPlant(debugProperty = "loggger.logcat"))
 *
 * // ADB 控制
 * adb shell setprop loggger.logcat false   # 关闭 Logcat 输出
 * adb shell setprop loggger.logcat true    # 开启
 * ```
 *
 * ### 自动分割线（每 N 条日志插入一条）
 * ```
 * Loggger.plant(LogcatPlant(divider = "─".repeat(30), dividerInterval = 5))
 * ```
 *
 * ### 手动分割线
 * ```
 * Loggger.plant(LogcatPlant(divider = "═".repeat(40)))
 * Loggger.d("Main", "第一段日志")
 * Loggger.divider()
 * Loggger.d("Main", "第二段日志")
 * ```
 *
 * ### 显示调用堆栈
 * ```
 * Loggger.plant(LogcatPlant(stackTraceDepth = 1))
 * ```
 *
 * ### 自定义输出样式
 * ```
 * Loggger.plant(LogcatPlant(formatter = LogFormatter.VERBOSE))
 * ```
 *
 * @param minLevel 最低日志级别（默认 [LogLevel.VERBOSE]）
 * @param stackTraceDepth 调用栈显示层数。0 = 不显示（默认）
 * @param formatter 自定义日志消息格式化器
 * @param divider 分割线文本。非空时支持自动/手动插入（默认 "" = 无分割线）
 * @param dividerInterval 自动分割线间隔。
 *   每 N 条日志自动输出一条分割线。0 = 禁用自动分割（默认）。
 *   仅在 [divider] 非空时生效。
 * @param debugProperty 调试系统属性名（如 `"loggger.logcat"`）。
 *   非空时，LogcatPlant 会定期读取此属性值：
 *   - 值为 `"false"` → 静默跳过所有日志输出
 *   - 值为其他或未设置 → 正常输出
 *   通过 `adb shell setprop <name> false` 可实时关闭 Logcat 输出。
 * @param debugPropertyRefreshMs 系统属性刷新间隔（毫秒，默认 5000）。
 *   避免每条日志都触发反射读取，仅在间隔到达时重新读取。
 */
class LogcatPlant(
    minLevel: LogLevel = LogLevel.VERBOSE,
    private val stackTraceDepth: Int = 0,
    private val formatter: LogFormatter? = null,
    private val divider: String = "",
    private val dividerInterval: Int = 0,
    private val debugProperty: String = "",
    private val debugPropertyRefreshMs: Long = 5000
) : LogPlant() {

    init {
        this.minLevel = minLevel
    }

    /** 日志计数器（用于自动分割线）。 */
    private var logCount = 0

    /** 系统属性缓存的开关值。 */
    private var propertyCachedEnabled = true

    /** 上次检查系统属性的时间戳。 */
    private var propertyLastCheckMs = 0L

    override fun performLog(entry: LogEntry) {
        // ADB 开关检查：属性值为 "false" 时跳过所有输出
        if (debugProperty.isNotEmpty() && !isDebugPropertyEnabled()) return

        // 自动分割线：检查是否需要先输出分割线
        if (divider.isNotEmpty() && dividerInterval > 0) {
            if (logCount > 0 && logCount % dividerInterval == 0) {
                printDividerLine(entry.tag)
            }
        }
        logCount++

        val message = buildOutputMessage(entry)
        val t = entry.throwable?.let { Throwable(it) }
        when (entry.level) {
            LogLevel.VERBOSE -> Log.v(entry.tag, message, t)
            LogLevel.DEBUG   -> Log.d(entry.tag, message, t)
            LogLevel.INFO    -> Log.i(entry.tag, message, t)
            LogLevel.WARN    -> Log.w(entry.tag, message, t)
            LogLevel.ERROR   -> Log.e(entry.tag, message, t)
            LogLevel.ASSERT  -> Log.wtf(entry.tag, message, t)
        }
    }

    /** 手动分割线 —— 由 [Loggger.divider] 触发。 */
    override fun onDivider() {
        if (divider.isNotEmpty()) {
            if (debugProperty.isEmpty() || isDebugPropertyEnabled()) {
                printDividerLine("─ DIVIDER ─")
            }
        }
    }

    // ── ADB 开关 ──

    /**
     * 检查系统属性是否允许日志输出。
     *
     * 使用缓存避免高频反射调用：
     * - 首次或距上次检查超过 [debugPropertyRefreshMs] 时重新读取
     * - 属性值为 "false" 返回 false，其他值返回 true
     */
    private fun isDebugPropertyEnabled(): Boolean {
        val now = System.currentTimeMillis()
        if (now - propertyLastCheckMs >= debugPropertyRefreshMs) {
            val value = SystemPropertyReader.get(debugProperty, "true")
            propertyCachedEnabled = value != "false"
            propertyLastCheckMs = now
        }
        return propertyCachedEnabled
    }

    // ── 分割线 ──

    private fun printDividerLine(tag: String) {
        Log.i(tag, divider)
    }

    // ── 消息构建 ──

    /**
     * 构建最终输出消息：
     * 1. 应用自定义 [formatter]（如果设置了）
     * 2. 追加调用栈信息（如果 [stackTraceDepth] > 0）
     */
    private fun buildOutputMessage(entry: LogEntry): String {
        // 1. 格式化消息正文
        val formattedMessage = formatter?.format(entry.message, entry.tag, entry.level)
            ?: entry.message

        // 2. 追加调用栈
        if (stackTraceDepth > 0) {
            val stackInfo = buildCallerStack()
            return if (stackInfo.isNotEmpty()) {
                "$formattedMessage\n$stackInfo"
            } else {
                formattedMessage
            }
        }

        return formattedMessage
    }

    /**
     * 从当前线程调用栈中提取调用者信息。
     * 跳过 Loggger / LogPlant / LogcatPlant 的内部帧，找到真正的调用者。
     *
     * 输出格式（[stackTraceDepth] = 1）：
     * ```
     *     at com.example.MainActivity.onCreate(MainActivity.kt:25)
     * ```
     *
     * 输出格式（[stackTraceDepth] = 3）：
     * ```
     *     at com.example.MainActivity.onCreate(MainActivity.kt:25)
     *     at com.example.MainActivity$callback.onClick(MainActivity.kt:40)
     *     at android.view.View.performClick(View.java:7506)
     * ```
     */
    private fun buildCallerStack(): String {
        val stack = Throwable().stackTrace
        val sb = StringBuilder()
        var found = false
        var count = 0

        for (element in stack) {
            val className = element.className

            // 跳过 Loggger 内部帧
            if (!found) {
                if (!className.startsWith("com.taisau.android.common.loggger")) {
                    found = true
                } else {
                    continue
                }
            }

            // 跳过 Java 反射 / 协程内部帧
            if (className.startsWith("kotlin.coroutines")) continue
            if (className.startsWith("kotlinx.coroutines")) continue
            if (className.startsWith("java.lang.reflect")) continue
            if (className.startsWith("android.os")) continue

            if (count < stackTraceDepth) {
                sb.append("\n    at ${className}.${element.methodName}(${element.fileName}:${element.lineNumber})")
                count++
            } else {
                break
            }
        }

        return sb.toString()
    }
}
