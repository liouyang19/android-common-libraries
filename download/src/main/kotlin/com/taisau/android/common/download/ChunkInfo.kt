package com.taisau.android.common.download

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 分片下载的单个切片信息。
 *
 * 每个分片负责下载文件的一个连续字节区间 [startByte, endByte]，
 * 通过 HTTP Range 头向服务器请求对应的数据段。
 */
data class ChunkInfo(
    /** 分片索引（从 0 开始）。 */
    val index: Int,

    /** 该分片的起始字节位置（含）。 */
    val startByte: Long,

    /** 该分片的结束字节位置（含）。 */
    val endByte: Long,

    /** 该分片已下载的字节数。 */
    val downloadedBytes: AtomicLong = AtomicLong(0),

    /** 该分片是否已完成。 */
    val isCompleted: AtomicBoolean = AtomicBoolean(false),

    /** 该分片是否已失败（超过重试次数）。 */
    val isFailed: AtomicBoolean = AtomicBoolean(false),

    /** 当前重试次数。 */
    val retryCount: AtomicLong = AtomicLong(0)
) {
    /** 该分片的总字节数。 */
    val totalBytes: Long get() = endByte - startByte + 1

    /** 该分片的下载进度（0f ~ 100f）。 */
    val progress: Float
        get() = if (totalBytes > 0) {
            downloadedBytes.get().toFloat() / totalBytes * 100
        } else 0f
}
