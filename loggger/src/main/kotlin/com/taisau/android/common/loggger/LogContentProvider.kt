package com.taisau.android.common.loggger

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.taisau.android.common.loggger.db.LogDatabase
import com.taisau.android.common.loggger.db.LogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * 跨进程日志访问的 ContentProvider。
 *
 * 提供以下 URI 操作：
 * - `content://<authority>/logs` — 日志集合操作（query / insert / delete）
 * - `content://<authority>/logs/#` — 单条日志操作（query / delete）
 *
 * authority 格式：`<applicationId>.loggger.provider`
 *
 * AndroidManifest 中声明：
 * ```xml
 * <provider
 *     android:name=".LogContentProvider"
 *     android:authorities="${applicationId}.loggger.provider"
 *     android:exported="true" />
 * ```
 */
class LogContentProvider : ContentProvider() {

    private lateinit var database: LogDatabase

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val appContext = context.applicationContext
        database = LogDatabase.getInstance(appContext)

        // 初始化静态 Contract URI
        val authority = "${appContext.packageName}${LogProviderContract.AUTHORITY_SUFFIX}"
        LogProviderContract.init(authority)

        // 清空过期日志（保留最近 7 天）
        runBlocking(Dispatchers.IO) {
            val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            database.logDao().deleteOlderThan(sevenDaysAgo)
        }

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val dao = database.logDao()
        val entries = runBlocking(Dispatchers.IO) {
            when (uriMatcher.match(uri)) {
                URI_LOGS -> {
                    // 解析分页参数（默认最近 200 条）
                    val limit = uri.getQueryParameter("limit")?.toIntOrNull() ?: 200
                    val offset = uri.getQueryParameter("offset")?.toIntOrNull() ?: 0
                    dao.getPaged(limit, offset)
                }
                URI_LOGS_ID -> {
                    val id = uri.lastPathSegment?.toLongOrNull() ?: return@runBlocking emptyList()
                    listOfNotNull(dao.getById(id))
                }
                else -> emptyList()
            }
        }
        return toCursor(entries, projection)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (values == null) return null
        val entity = LogEntity.fromContentValues(values)
        runBlocking(Dispatchers.IO) {
            database.logDao().insert(entity)
        }
        return Uri.withAppendedPath(uri, entity.id.toString())
    }

    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int {
        val entities = values.mapNotNull { LogEntity.fromContentValues(it) }
        runBlocking(Dispatchers.IO) {
            database.logDao().insertAll(entities)
        }
        return entities.size
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        // 日志记录不支持更新
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return runBlocking(Dispatchers.IO) {
            when (uriMatcher.match(uri)) {
                URI_LOGS -> {
                    if (selection == null) {
                        database.logDao().deleteAll()
                        Int.MAX_VALUE
                    } else {
                        // 按时间清理: selection = "timestamp < ?"
                        val beforeTime = selectionArgs?.firstOrNull()?.toLongOrNull()
                        if (beforeTime != null) {
                            database.logDao().deleteOlderThan(beforeTime)
                            Int.MAX_VALUE
                        } else 0
                    }
                }
                URI_LOGS_ID -> {
                    val id = uri.lastPathSegment?.toLongOrNull() ?: return@runBlocking 0
                    database.logDao().deleteById(id)
                    1
                }
                else -> 0
            }
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            URI_LOGS -> "vnd.android.cursor.dir/vnd.taisau.log"
            URI_LOGS_ID -> "vnd.android.cursor.item/vnd.taisau.log"
            else -> null
        }
    }

    // ── 工具方法 ──

    /**
     * 将 [LogEntity] 列表转换为 [MatrixCursor]。
     * 若指定了 [projection]，只返回投影中的列。
     */
    private fun toCursor(entities: List<LogEntity>, projection: Array<out String>?): MatrixCursor {
        val columns = if (projection.isNullOrEmpty()) {
            LogEntry.ALL_COLUMNS
        } else {
            projection
        }
        val cursor = MatrixCursor(columns)
        for (entity in entities) {
            val domain = entity.toDomain()
            val row = columns.map { col ->
                when (col) {
                    LogEntry.COL_ID -> domain.id
                    LogEntry.COL_LEVEL -> domain.level.priority
                    LogEntry.COL_TAG -> domain.tag
                    LogEntry.COL_MESSAGE -> domain.message
                    LogEntry.COL_THROWABLE -> domain.throwable
                    LogEntry.COL_TIMESTAMP -> domain.timestamp
                    LogEntry.COL_PID -> domain.pid
                    LogEntry.COL_TID -> domain.tid
                    LogEntry.COL_PROCESS_NAME -> domain.processName
                    else -> null
                }
            }
            cursor.addRow(row)
        }
        return cursor
    }

    companion object {
        private const val URI_LOGS = 1
        private const val URI_LOGS_ID = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI("*", LogProviderContract.BASE_PATH, URI_LOGS)
            addURI("*", "${LogProviderContract.BASE_PATH}/#", URI_LOGS_ID)
        }
    }
}
