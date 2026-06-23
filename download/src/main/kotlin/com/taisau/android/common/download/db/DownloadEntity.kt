package com.taisau.android.common.download.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.taisau.android.common.download.TaskStatus

/**
 * 下载记录持久化实体 —— 对应 [TABLE_NAME] 表。
 *
 * 使用 [url]+[filePath]+[fileName] 的 MD5 作为主键 [id]，
 * 确保同一文件的多次下载请求能自动复用断点续传记录。
 */
@Entity(tableName = DownloadEntity.TABLE_NAME)
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val url: String = "",
    val fileName: String = "",
    val filePath: String = "",
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: String = TaskStatus.PENDING.name,
    val headers: String = "",              // JSON 序列化
    val errorMessage: String? = null,
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis(),
    val priority: Int = 0,                  // 下载优先级
    val etag: String? = null,              // 服务端 ETag，用于文件变更校验
    val lastModified: String? = null       // 服务端 Last-Modified，用于文件变更校验
) {
    companion object {
        const val TABLE_NAME = "download_entities"
    }
}
