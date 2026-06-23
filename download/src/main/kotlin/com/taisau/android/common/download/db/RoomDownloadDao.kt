package com.taisau.android.common.download.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.taisau.android.common.download.DownloadInfo
import com.taisau.android.common.download.DownloadPriority
import com.taisau.android.common.download.TaskStatus
import com.taisau.android.common.download.toTaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [DownloadDao] 的 Room 实现。
 *
 * 启用 WAL 模式以提升并发读写性能（Gemini 建议）。
 */
class RoomDownloadDao(context: Context) : DownloadDao {

    private val dao = Room.databaseBuilder(
        context.applicationContext,
        DownloadRoomDatabase::class.java,
        DB_NAME,
    ).fallbackToDestructiveMigration(false)
        .build()
        .downloadEntityDao()

    // ── 主任务 ──

    override suspend fun insertOrUpdate(entity: DownloadEntity) {
        dao.insertOrUpdate(entity)
    }

    override suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long) {
        dao.updateProgress(id, downloadedBytes, totalBytes, System.currentTimeMillis())
    }

    override suspend fun updateStatus(id: String, status: String) {
        dao.updateStatus(id, status, System.currentTimeMillis())
    }

    override suspend fun getById(id: String): DownloadEntity? = dao.getById(id)

    override suspend fun getDownloadInfo(id: String): DownloadInfo? {
        return dao.getById(id)?.toDomain()
    }

    override fun getAllDownloads(): Flow<List<DownloadInfo>> {
        return dao.getAllDownloads().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getUnfinishedTasks(): List<DownloadInfo> {
        return withContext(Dispatchers.IO) {
            dao.getUnfinishedTasks().map { it.toDomain() }
        }
    }

    override suspend fun getIncompleteByUrl(url: String): DownloadEntity? {
        return dao.getIncompleteByUrl(url)
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun clearCompleted() {
        dao.clearCompleted(TaskStatus.COMPLETED.name)
    }

    // ── 分片任务 ──

    override suspend fun insertOrUpdateChunks(chunks: List<ChunkEntity>) {
        dao.insertOrUpdateChunks(chunks)
    }

    override suspend fun getChunksByTaskId(taskId: String): List<ChunkEntity> {
        return dao.getChunksByTaskId(taskId)
    }

    override suspend fun deleteChunksByTaskId(taskId: String) {
        dao.deleteChunksByTaskId(taskId)
    }

    companion object {
        private const val DB_NAME = "download_database.db"
    }
}

// ── Entity <-> Domain 转换 ──

internal fun DownloadEntity.toDomain(): DownloadInfo {
    val headersMap = if (headers.isNotEmpty()) {
        try {
            com.taisau.android.common.download.utils.HeadersSerializer.parse(headers)
        } catch (_: Exception) {
            emptyMap()
        }
    } else emptyMap()

    return DownloadInfo(
        id = id,
        url = url,
        fileName = fileName,
        filePath = filePath,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status.toTaskStatus(),
        headers = headersMap,
        errorMessage = errorMessage,
        createTime = createTime,
        updateTime = updateTime,
        priority = when {
            priority >= DownloadPriority.HIGH.ordinal -> DownloadPriority.HIGH
            priority <= DownloadPriority.LOW.ordinal -> DownloadPriority.LOW
            else -> DownloadPriority.NORMAL
        },
        etag = etag,
        lastModified = lastModified
    )
}

internal fun DownloadInfo.toEntity(): DownloadEntity {
    val headersStr = if (headers.isNotEmpty()) {
        try {
            com.taisau.android.common.download.utils.HeadersSerializer.serialize(headers)
        } catch (_: Exception) {
            ""
        }
    } else ""

    return DownloadEntity(
        id = id,
        url = url,
        fileName = fileName,
        filePath = filePath,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status.name,
        headers = headersStr,
        errorMessage = errorMessage,
        createTime = createTime,
        updateTime = updateTime,
        priority = priority.ordinal,
        etag = etag,
        lastModified = lastModified
    )
}

// ── Room 内部组件 ──

@Dao
internal interface DownloadEntityDao {
    // 主任务
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: DownloadEntity)

    @Query("UPDATE download_entities SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, updateTime = :updateTime WHERE id = :id")
    suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long, updateTime: Long)

    @Query("UPDATE download_entities SET status = :status, updateTime = :updateTime WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updateTime: Long)

    @Query("SELECT * FROM download_entities WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM download_entities ORDER BY updateTime DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_entities WHERE status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') ORDER BY updateTime DESC")
    suspend fun getUnfinishedTasks(): List<DownloadEntity>

    @Query("SELECT * FROM download_entities WHERE url = :url AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') ORDER BY updateTime DESC LIMIT 1")
    suspend fun getIncompleteByUrl(url: String): DownloadEntity?

    @Query("DELETE FROM download_entities WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM download_entities WHERE status = :status")
    suspend fun clearCompleted(status: String)

    // 分片
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateChunks(chunks: List<ChunkEntity>)

    @Query("SELECT * FROM download_chunk_entities WHERE taskId = :taskId ORDER BY chunkIndex ASC")
    suspend fun getChunksByTaskId(taskId: String): List<ChunkEntity>

    @Query("DELETE FROM download_chunk_entities WHERE taskId = :taskId")
    suspend fun deleteChunksByTaskId(taskId: String)
}

@Database(
    entities = [DownloadEntity::class, ChunkEntity::class],
    version = 2,
    exportSchema = false
)
internal abstract class DownloadRoomDatabase : RoomDatabase() {
    abstract fun downloadEntityDao(): DownloadEntityDao
}
