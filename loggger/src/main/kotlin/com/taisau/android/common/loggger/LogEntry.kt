@file:Suppress("unused")

package com.taisau.android.common.loggger

/**
 * 日志条目 —— 表示一条完整的日志记录。
 *
 * 作为跨进程传递的领域模型，同时也作为 ContentProvider 的
 * 数据交换载体（通过 [toContentValues] / [fromCursor] 转换）。
 */
data class LogEntry(
    /** 唯一 ID（毫秒时间戳 + 自增序列）。 */
    val id: Long = 0L,
    /** 日志级别。 */
    val level: LogLevel = LogLevel.DEBUG,
    /** 日志标签。 */
    val tag: String = "",
    /** 日志消息正文。 */
    val message: String = "",
    /** 异常堆栈（可选）。 */
    val throwable: String? = null,
    /** 记录时间（毫秒时间戳）。 */
    val timestamp: Long = System.currentTimeMillis(),
    /** 来源进程 ID。 */
    val pid: Int = android.os.Process.myPid(),
    /** 来源线程 ID。 */
    val tid: Int = android.os.Process.myTid(),
    /** 来源进程名（可选，由 Loggger 初始化时设定）。 */
    val processName: String? = null
) {
    companion object {
        /** ContentProvider 中用到的列名。 */
        const val COL_ID = "_id"
        const val COL_LEVEL = "level"
        const val COL_TAG = "tag"
        const val COL_MESSAGE = "message"
        const val COL_THROWABLE = "throwable"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_PID = "pid"
        const val COL_TID = "tid"
        const val COL_PROCESS_NAME = "process_name"

        /** 全部列名数组（供 ContentProvider 投影使用）。 */
        val ALL_COLUMNS = arrayOf(
            COL_ID, COL_LEVEL, COL_TAG, COL_MESSAGE, COL_THROWABLE,
            COL_TIMESTAMP, COL_PID, COL_TID, COL_PROCESS_NAME
        )
    }
}
