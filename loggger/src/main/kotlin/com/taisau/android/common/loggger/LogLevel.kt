@file:Suppress("unused")

package com.taisau.android.common.loggger

import android.util.Log

/**
 * 日志级别枚举。
 *
 * 与 Android [Log] 的优先级常量一一对应，
 * 同时支持按级别过滤。
 */
enum class LogLevel(val priority: Int) {
    VERBOSE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR),
    ASSERT(Log.ASSERT);

    companion object {
        /**
         * 根据 Android [Log] 优先级 int 值转换为对应的 [LogLevel]。
         */
        fun fromPriority(priority: Int): LogLevel {
            return entries.firstOrNull { it.priority == priority } ?: DEBUG
        }
    }
}
