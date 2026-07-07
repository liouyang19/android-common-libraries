package com.taisau.android.common.loggger.db

import android.content.ContentValues
import android.database.Cursor
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.taisau.android.common.loggger.LogEntry
import com.taisau.android.common.loggger.LogLevel

/**
 * 日志记录 Room 实体 —— 对应 [TABLE_NAME] 表。
 */
@Entity(tableName = LogEntity.TABLE_NAME)
data class LogEntity(
    @PrimaryKey
    val id: Long,
    val level: Int,
    val tag: String,
    val message: String,
    val throwable: String?,
    val timestamp: Long,
    val pid: Int,
    val tid: Int,
    val processName: String?
) {
    companion object {
        const val TABLE_NAME = "log_entries"

        /** 从 [LogEntry] 领域模型创建实体。 */
        fun fromDomain(entry: LogEntry): LogEntity = LogEntity(
            id = entry.id,
            level = entry.level.priority,
            tag = entry.tag,
            message = entry.message,
            throwable = entry.throwable,
            timestamp = entry.timestamp,
            pid = entry.pid,
            tid = entry.tid,
            processName = entry.processName
        )

        /** 从 ContentValues (ContentProvider insert) 创建实体。 */
        fun fromContentValues(values: ContentValues): LogEntity {
            val now = System.currentTimeMillis()
            return LogEntity(
                id = values.getAsLong(LogEntry.COL_ID) ?: now,
                level = values.getAsInteger(LogEntry.COL_LEVEL) ?: LogLevel.DEBUG.priority,
                tag = values.getAsString(LogEntry.COL_TAG) ?: "",
                message = values.getAsString(LogEntry.COL_MESSAGE) ?: "",
                throwable = values.getAsString(LogEntry.COL_THROWABLE),
                timestamp = values.getAsLong(LogEntry.COL_TIMESTAMP) ?: now,
                pid = values.getAsInteger(LogEntry.COL_PID) ?: android.os.Process.myPid(),
                tid = values.getAsInteger(LogEntry.COL_TID) ?: android.os.Process.myTid(),
                processName = values.getAsString(LogEntry.COL_PROCESS_NAME)
            )
        }
    }

    /** 转为领域模型 [LogEntry]。 */
    fun toDomain(): LogEntry = LogEntry(
        id = id,
        level = LogLevel.fromPriority(level),
        tag = tag,
        message = message,
        throwable = throwable,
        timestamp = timestamp,
        pid = pid,
        tid = tid,
        processName = processName
    )
}
