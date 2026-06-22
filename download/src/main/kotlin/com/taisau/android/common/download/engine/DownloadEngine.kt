package com.taisau.android.common.download.engine

import com.taisau.android.common.download.DownloadConfig
import com.taisau.android.common.download.download.DownloadRequest
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * 下载引擎 —— 负责实际的 HTTP 通信。
 *
 * 默认实现 [OkHttpEngine] 基于 OkHttp，
 * 使用者可传入自定义引擎以获得专用连接池、拦截器等能力。
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
     * 执行一次 HTTP 请求。
     *
     * @param request  请求参数
     * @return [DownloadResponse] 响应信息
     */
    suspend fun execute(request: DownloadRequest): DownloadResponse

    /**
     * 分片下载 —— 以 Flow 形式流式发射数据块。
     *
     * @param request        下载请求
     * @param startPosition  开始字节位置
     * @param endPosition    结束字节位置（-1 表示到文件末尾）
     * @param config         下载配置
     * @return 数据块 Flow
     */
    suspend fun download(
        request: DownloadRequest,
        startPosition: Long,
        endPosition: Long,
        config: DownloadConfig
    ): Flow<DownloadChunk>
}

/**
 * HTTP 响应信息。
 */
data class DownloadResponse(
    val responseCode: Int,
    val contentLength: Long,
    val inputStream: InputStream?,
    val headers: Map<String, String> = emptyMap(),
) {
    val isPartial: Boolean get() = responseCode == 206
}

/**
 * 下载数据块。
 */
data class DownloadChunk(
    val bytes: ByteArray,
    val offset: Long,
    val totalBytes: Long,
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloadChunk) return false
        return offset == other.offset &&
                totalBytes == other.totalBytes &&
                chunkIndex == other.chunkIndex &&
                totalChunks == other.totalChunks &&
                bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + totalBytes.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        return result
    }
}
