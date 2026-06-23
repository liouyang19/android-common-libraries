package com.taisau.android.common.download.db

import androidx.room.Entity
import androidx.room.Index

/**
 * 分片下载的切片持久化实体。
 *
 * 每个分片独立记录已下载的字节数，实现"切片级"断点续传。
 * 当 App 被强杀后重新启动时，[ChunkedDownloader][com.taisau.android.common.download.download.ChunkedDownloader]
 * 通过此表恢复各分片的进度，无需从 0 开始。
 *
 * 复合主键：(taskId + chunkIndex)，确保同一个任务的每个分片唯一。
 */
@Entity(
    tableName = ChunkEntity.TABLE_NAME,
    primaryKeys = ["taskId", "chunkIndex"],
    indices = [Index(value = ["taskId"])]
)
data class ChunkEntity(
    /** 所属主任务的 ID（对应 [DownloadEntity.id]）。 */
    val taskId: String,

    /** 分片索引（从 0 开始）。 */
    val chunkIndex: Int,

    /** 该分片的起始字节位置（含）。 */
    val startByte: Long,

    /** 该分片的结束字节位置（含）。 */
    val endByte: Long,

    /** 该分片已下载的字节数。 */
    val downloadedBytes: Long = 0,

    /** 是否已完成。 */
    val isCompleted: Boolean = false,

    /** 是否已失败。 */
    val isFailed: Boolean = false
) {
    companion object {
        const val TABLE_NAME = "download_chunk_entities"
    }
}
