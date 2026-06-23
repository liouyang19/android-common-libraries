package com.taisau.android.common.download.engine

import com.taisau.android.common.download.DownloadConfig
import com.taisau.android.common.download.download.DownloadRequest
import okio.BufferedSource
import java.io.InputStream

/**
 * 下载引擎 —— 负责实际的 HTTP 通信。
 *
 * 实现类需返回响应体 [DownloadResponse.body]，类型可以是：
 * - [BufferedSource]（默认 [OkHttpEngine] 使用 Okio）
 * - [InputStream]（自定义引擎可返回标准 Java 流）
 * - 其他任意类型（需确保 [DownloadTask][com.taisau.android.common.download.download.DownloadTask] 能读取）
 *
 * [DownloadTask] 会按 [BufferedSource] → [InputStream] 的顺序尝试向下转型读取。
 */
interface DownloadEngine {

    /**
     * 获取文件大小（通过 HEAD 请求）。
     */
    suspend fun getContentLength(request: DownloadRequest, config: DownloadConfig): Long

    /**
     * 判断服务器是否支持分片下载（Range 请求）。
     */
    suspend fun supportRange(request: DownloadRequest, config: DownloadConfig): Boolean

    /**
     * 执行一次 HTTP 请求，返回响应信息和可读的响应体。
     *
     * @param request  请求参数
     * @return [DownloadResponse] 响应信息，[DownloadResponse.body] 是响应体
     */
    suspend fun execute(request: DownloadRequest): DownloadResponse
}

/**
 * HTTP 响应信息。
 *
 * @param responseCode  HTTP 状态码（200、206 等）
 * @param contentLength 响应体长度（-1 表示未知）
 * @param body          响应体。默认引擎 [OkHttpEngine] 返回 [BufferedSource]，
 *                      自定义引擎可返回 [InputStream] 或其他流类型。
 * @param headers       响应头
 */
data class DownloadResponse(
    val responseCode: Int,
    val contentLength: Long,
    val body: Any? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    val isPartial: Boolean get() = responseCode == 206
}
