@file:Suppress("unused")

package com.taisau.android.common.loggger

/**
 * 日志格式化器 —— 用于自定义 [LogcatPlant] 的输出样式。
 *
 * ### 内置实现
 * - [LogFormatter.DEFAULT] → 仅输出消息正文
 * - [LogFormatter.WITH_TIMESTAMP] → 带时间戳 `[HH:mm:ss.SSS] message`
 * - [LogFormatter.WITH_LEVEL] → 带级别图标 `[✓] message` / `[✗] message`
 * - [LogFormatter.VERBOSE] → 完整格式 `[HH:mm:ss.SSS] [LEVEL] [Tag] message`
 *
 * ### 自定义示例
 * ```
 * Loggger.plant(LogcatPlant(
 *     formatter = LogFormatter { tag, message, level ->
 *         "[${level.name.first()}] [$tag] $message"
 *     }
 * ))
 * ```
 *
 * @see LogcatPlant
 */
fun interface LogFormatter {

    /**
     * 格式化日志消息。
     *
     * @param message 原始日志消息正文
     * @param tag 日志标签
     * @param level 日志级别
     * @return 格式化后的日志字符串（将作为 Log.w/tag 的第二个参数输出）
     */
    fun format(message: String, tag: String, level: LogLevel): String

    companion object {

        /** 默认格式 — 仅输出消息正文。 */
        @JvmStatic
        val DEFAULT: LogFormatter = LogFormatter { message, _, _ -> message }

        /** 带时间戳 — `[10:30:00.123] message`。 */
        @JvmStatic
        val WITH_TIMESTAMP: LogFormatter = LogFormatter { message, _, _ ->
            val time = java.text.SimpleDateFormat(
                "HH:mm:ss.SSS", java.util.Locale.getDefault()
            ).format(java.util.Date())
            "[$time] $message"
        }

        /** 带级别图标 — `[✓] message` / `[✗] message`。 */
        @JvmStatic
        val WITH_LEVEL: LogFormatter = LogFormatter { message, _, level ->
            val icon = when (level) {
                LogLevel.VERBOSE -> "·"
                LogLevel.DEBUG -> "🐛"
                LogLevel.INFO -> "✓"
                LogLevel.WARN -> "⚠"
                LogLevel.ERROR -> "✗"
                LogLevel.ASSERT -> "‼"
            }
            "$icon $message"
        }

        /** 完整格式 — `[10:30:00.123] [DEBUG] [Tag] message`。 */
        @JvmStatic
        val VERBOSE: LogFormatter = LogFormatter { message, tag, level ->
            val time = java.text.SimpleDateFormat(
                "HH:mm:ss.SSS", java.util.Locale.getDefault()
            ).format(java.util.Date())
            "[$time] [${level.name}] [$tag] $message"
        }
    }
}
