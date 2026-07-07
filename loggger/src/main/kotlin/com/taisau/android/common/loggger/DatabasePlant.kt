@file:Suppress("unused")

package com.taisau.android.common.loggger

import android.content.Context

/**
 * 数据库持久化 Plant —— 将日志通过 ContentProvider 写入 Room 数据库。
 *
 * 日志数据可通过跨进程 [LogProviderContract] 被其他进程读取。
 *
 * ```
 * Loggger.plant(LogcatPlant())
 * Loggger.plant(DatabasePlant(context))
 * ```
 *
 * @param context Application Context
 * @param minLevel 最低日志级别（默认 [LogLevel.VERBOSE]）
 */
class DatabasePlant(
    context: Context,
    minLevel: LogLevel = LogLevel.VERBOSE
) : LogPlant() {

    init {
        this.minLevel = minLevel
    }

    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val logUri = LogProviderContract.CONTENT_URI

    override fun performLog(entry: LogEntry) {
        try {
            val values = LogProviderContract.toContentValues(entry)
            contentResolver.insert(logUri, values)
        } catch (_: Exception) {
            // ContentProvider 不可用时静默降级
        }
    }
}
