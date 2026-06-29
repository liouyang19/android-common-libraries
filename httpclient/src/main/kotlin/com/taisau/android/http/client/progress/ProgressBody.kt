package com.taisau.android.http.client.progress

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

// ═══════════════════════════════════════════════
// 进度监听器
// ═══════════════════════════════════════════════

/** 进度回调 */
fun interface ProgressListener {
    /**
     * @param bytesRead  已读取/写入的字节数
     * @param totalBytes 总字节数（-1 表示未知）
     * @param speedBps   当前速度（字节/秒）
     */
    fun onProgress(bytesRead: Long, totalBytes: Long, speedBps: Long)
}

/** 下载配置 */
data class DownloadConfig(
    /** 目标文件 */
    val destFile: java.io.File? = null,
    /** 进度回调 */
    val onProgress: ((Long, Long, Long) -> Unit)? = null,
    /** 下载限速（字节/秒），0=不限速 */
    val speedLimit: Long = 0,
)

// ═══════════════════════════════════════════════
// 上传进度包装
// ═══════════════════════════════════════════════

/**
 * 包装 [RequestBody]，在上传时回调进度。
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val listener: ProgressListener,
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: okio.BufferedSink) {
        val total = contentLength()
        val countingSink = CountingSink(sink, total, listener)
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}

private class CountingSink(
    delegate: okio.Sink,
    private val total: Long,
    private val listener: ProgressListener,
) : okio.ForwardingSink(delegate) {

    private var bytesWritten = 0L
    private var lastTime = System.nanoTime()
    private var lastBytes = 0L

    override fun write(source: Buffer, byteCount: Long) {
        super.write(source, byteCount)
        bytesWritten += byteCount
        val now = System.nanoTime()
        val elapsed = now - lastTime

        val speed = if (elapsed > 0) {
            ((bytesWritten - lastBytes) * 1_000_000_000L / elapsed)
        } else 0L

        listener.onProgress(bytesWritten, total, speed)

        if (elapsed >= 1_000_000_000L) {
            lastTime = now
            lastBytes = bytesWritten
        }
    }
}

// ═══════════════════════════════════════════════
// 下载进度包装 + 限速
// ═══════════════════════════════════════════════

/**
 * 包装 [ResponseBody]，在下载时回调进度并可选限速。
 */
class ProgressResponseBody(
    private val delegate: ResponseBody,
    private val listener: ProgressListener,
    /** 下载限速（字节/秒），0 = 不限速 */
    private val speedLimit: Long = 0,
) : ResponseBody() {

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun source(): BufferedSource {
        val delegateSource = delegate.source()
        val total = contentLength()
        return ProgressSource(delegateSource, total, listener, speedLimit).buffer()
    }
}

/**
 * 带进度 + 限速的 Source。
 *
 * 限速算法：滑动窗口（每秒重置），超过配额则 sleep 到下一秒。
 */
private class ProgressSource(
    delegate: Source,
    private val total: Long,
    private val listener: ProgressListener,
    private val speedLimit: Long,
) : ForwardingSource(delegate) {

    private var bytesRead = 0L
    private var windowStart = System.nanoTime()
    private var windowBytes = 0L
    private var lastReportTime = System.nanoTime()
    private var lastReportBytes = 0L
    private var speedBps = 0L

    override fun read(sink: Buffer, byteCount: Long): Long {
        // 限速检查
        if (speedLimit > 0) throttle()

        val bytes = super.read(sink, byteCount)

        if (bytes != -1L) {
            bytesRead += bytes
            windowBytes += bytes
            reportProgress()
        }

        return bytes
    }

    private fun throttle() {
        val now = System.nanoTime()
        val elapsed = now - windowStart

        if (elapsed >= 1_000_000_000L) {
            // 新窗口
            windowStart = now
            windowBytes = 0
            return
        }

        if (windowBytes >= speedLimit) {
            // 超过配额 → sleep 到窗口结束
            val sleepMs = (1_000_000_000L - elapsed) / 1_000_000L
            if (sleepMs > 0) Thread.sleep(sleepMs)
            windowStart = System.nanoTime()
            windowBytes = 0
        }
    }

    private fun reportProgress() {
        val now = System.nanoTime()
        val elapsed = now - lastReportTime
        if (elapsed >= 500_000_000L) {  // 每 500ms 计算一次速度
            speedBps = ((bytesRead - lastReportBytes) * 1_000_000_000L / elapsed)
                .coerceAtLeast(0)
            lastReportTime = now
            lastReportBytes = bytesRead
        }
        listener.onProgress(bytesRead, total, speedBps)
    }
}
