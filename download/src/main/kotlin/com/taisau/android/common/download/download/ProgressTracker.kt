package com.taisau.android.common.download.download

import com.taisau.android.common.download.db.DownloadDao
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 进度追踪器 —— 防止进度倒退。
 *
 * 使用 CAS（Compare-And-Swap）确保进度值单向递增，
 * 避免网络重试、分片乱序等场景下进度值回退。
 *
 * @param downloadDao 用于持久化进度
 */
class ProgressTracker(private val downloadDao: DownloadDao) {

    private val progressCache = ConcurrentHashMap<String, AtomicLong>()
    private val totalBytesCache = ConcurrentHashMap<String, AtomicLong>()

    fun initialize(downloadId: String, initialProgress: Long) {
        progressCache.putIfAbsent(downloadId, AtomicLong(initialProgress))
    }

    fun getProgress(downloadId: String): Long {
        return progressCache[downloadId]?.get() ?: 0L
    }

    /**
     * 保存进度 —— 仅当新进度 > 旧进度时才写入。
     */
    suspend fun saveProgress(downloadId: String, progress: Long, totalBytes: Long = 0) {
        val currentProgress = progressCache.getOrPut(downloadId) { AtomicLong(0) }
        val oldProgress = currentProgress.get()
        if (progress > oldProgress) {
            if (currentProgress.compareAndSet(oldProgress, progress)) {
                val total = if (totalBytes > 0) totalBytes
                else totalBytesCache[downloadId]?.get() ?: 0
                downloadDao.updateProgress(downloadId, progress, total)
            }
        }
        if (totalBytes > 0) {
            totalBytesCache.getOrPut(downloadId) { AtomicLong(0) }.set(totalBytes)
        }
    }

    /**
     * 从数据库恢复进度到内存缓存。
     */
    suspend fun restoreProgress(downloadId: String): Long {
        val downloadInfo = downloadDao.getDownloadInfo(downloadId)
        return if (downloadInfo != null) {
            val progress = downloadInfo.downloadedBytes
            progressCache[downloadId] = AtomicLong(progress)
            progress
        } else {
            0L
        }
    }

    fun clear(downloadId: String) {
        progressCache.remove(downloadId)
        totalBytesCache.remove(downloadId)
    }
}
