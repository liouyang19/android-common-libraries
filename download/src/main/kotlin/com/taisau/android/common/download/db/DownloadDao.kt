package com.taisau.android.common.download.db

import com.taisau.android.common.download.DownloadInfo
import kotlinx.coroutines.flow.Flow

/**
 * 下载记录数据访问接口。
 *
 * 模块内置默认实现 [RoomDownloadDao] 基于 Room，
 * 使用者可传入自定义 [DownloadDao] 实现，以复用已有数据库框架。
 */
interface DownloadDao {

    // ── 主任务 ──

    /** 插入或替换主任务记录。 */
    suspend fun insertOrUpdate(entity: DownloadEntity)

    /** 更新主任务进度。 */
    suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long)

    /** 更新主任务状态。 */
    suspend fun updateStatus(id: String, status: String)

    /** 根据主键查询主任务实体。 */
    suspend fun getById(id: String): DownloadEntity?

    /** 根据主键查询主任务并转为 [DownloadInfo]。 */
    suspend fun getDownloadInfo(id: String): DownloadInfo?

    /** 查询所有主任务（响应式 Flow）。 */
    fun getAllDownloads(): Flow<List<DownloadInfo>>

    /** 查询所有未完成的主任务。 */
    suspend fun getUnfinishedTasks(): List<DownloadInfo>

    /** 根据 URL 查询未完成的主任务（用于断点续传恢复）。 */
    suspend fun getIncompleteByUrl(url: String): DownloadEntity?

    /** 删除主任务。 */
    suspend fun delete(id: String)

    /** 清空已完成的记录。 */
    suspend fun clearCompleted()

    // ── 分片任务 ──

    /** 批量保存分片进度（插入或替换）。 */
    suspend fun insertOrUpdateChunks(chunks: List<ChunkEntity>)

    /** 查询某个主任务的所有分片。 */
    suspend fun getChunksByTaskId(taskId: String): List<ChunkEntity>

    /** 删除某个主任务的所有分片记录。 */
    suspend fun deleteChunksByTaskId(taskId: String)
}
