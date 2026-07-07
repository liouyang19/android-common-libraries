@file:Suppress("unused")

package com.taisau.android.common.loggger

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * [LogContentProvider] 的公开契约类。
 *
 * 跨进程访问日志时使用此类提供的常量和方法：
 * ```
 * // 写入日志（跨进程）
 * val values = LogProviderContract.toContentValues(
 *     LogEntry(level = LogLevel.ERROR, tag = "Crash", message = "OOM")
 * )
 * context.contentResolver.insert(LogProviderContract.CONTENT_URI, values)
 *
 * // 读取日志（跨进程）
 * val cursor = context.contentResolver.query(
 *     LogProviderContract.CONTENT_URI,
 *     null, null, null, "${LogEntry.COL_TIMESTAMP} DESC LIMIT 100"
 * )
 * cursor?.use {
 *     while (it.moveToNext()) {
 *         val entry = LogProviderContract.fromCursor(it)
 *     }
 * }
 * ```
 */
object LogProviderContract {

    /** ContentProvider 的 authority。 */
    const val AUTHORITY_SUFFIX = ".loggger.provider"

    /** 日志记录表的基 URI。 */
    const val BASE_PATH = "logs"

    /** 根据 applicationId 构建完整的 Content URI。 */
    fun buildContentUri(authority: String): Uri {
        return Uri.parse("content://$authority/$BASE_PATH")
    }

    /** 默认的 Content URI（初始化前不可用，须动态获取）。 */
    @Volatile
    lateinit var CONTENT_URI: Uri
        private set

    /** 初始化 Content URI（由 [LogContentProvider.onCreate] 自动调用）。 */
    internal fun init(authority: String) {
        CONTENT_URI = buildContentUri(authority)
    }

    // ── 转换方法 ──

    /** 将 [LogEntry] 转换为 [ContentValues]（用于 insert）。 */
    fun toContentValues(entry: LogEntry): ContentValues {
        return ContentValues().apply {
            put(LogEntry.COL_ID, entry.id)
            put(LogEntry.COL_LEVEL, entry.level.priority)
            put(LogEntry.COL_TAG, entry.tag)
            put(LogEntry.COL_MESSAGE, entry.message)
            put(LogEntry.COL_THROWABLE, entry.throwable)
            put(LogEntry.COL_TIMESTAMP, entry.timestamp)
            put(LogEntry.COL_PID, entry.pid)
            put(LogEntry.COL_TID, entry.tid)
            put(LogEntry.COL_PROCESS_NAME, entry.processName)
        }
    }

    /** 从 [Cursor] 读取 [LogEntry]。 */
    fun fromCursor(cursor: Cursor): LogEntry {
        return LogEntry(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(LogEntry.COL_ID)),
            level = LogLevel.fromPriority(cursor.getInt(cursor.getColumnIndexOrThrow(LogEntry.COL_LEVEL))),
            tag = cursor.getString(cursor.getColumnIndexOrThrow(LogEntry.COL_TAG)) ?: "",
            message = cursor.getString(cursor.getColumnIndexOrThrow(LogEntry.COL_MESSAGE)) ?: "",
            throwable = cursor.getString(cursor.getColumnIndexOrThrow(LogEntry.COL_THROWABLE)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(LogEntry.COL_TIMESTAMP)),
            pid = cursor.getInt(cursor.getColumnIndexOrThrow(LogEntry.COL_PID)),
            tid = cursor.getInt(cursor.getColumnIndexOrThrow(LogEntry.COL_TID)),
            processName = cursor.getString(cursor.getColumnIndexOrThrow(LogEntry.COL_PROCESS_NAME))
        )
    }
}
