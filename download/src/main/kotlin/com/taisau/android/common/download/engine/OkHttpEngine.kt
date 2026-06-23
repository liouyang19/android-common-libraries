package com.taisau.android.common.download.engine

import com.taisau.android.common.download.DownloadConfig
import com.taisau.android.common.download.download.DownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 基于 OkHttp 的 [DownloadEngine] 实现。
 *
 * 支持连接复用、TLS 握手优化、自动重定向、Range 断点续传。
 * 单个实例可安全地并发使用，内部维护连接池和线程池。
 *
 * [execute] 返回的 [DownloadResponse.body] 类型为 [BufferedSource]（Okio 流），
 * 调用方使用完毕后应关闭该 source。
 */
class OkHttpEngine(
    private val customClient: OkHttpClient? = null,
) : DownloadEngine {

    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_CONNECT_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(DEFAULT_READ_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    override suspend fun getContentLength(
        request: DownloadRequest,
        config: DownloadConfig
    ): Long = withContext(Dispatchers.IO) {
        val headReq = Request.Builder()
            .url(request.url)
            .head()
            .apply { request.headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        val client = resolveClient(request)
        val response = client.newCall(headReq).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("获取文件大小失败: HTTP ${resp.code}")
            }
            val contentLength = resp.header("Content-Length")?.toLongOrNull()
            if (contentLength != null && contentLength > 0) {
                return@use contentLength
            }
            val getReq = Request.Builder()
                .url(request.url)
                .header("Range", "bytes=0-0")
                .apply { request.headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()
            val getResp = client.newCall(getReq).execute()
            getResp.use {
                val length = it.header("Content-Length")?.toLongOrNull()
                    ?: it.body.contentLength()
                if (length <= 0) throw IOException("无法获取文件大小")
                length
            }
        }
    }

    override suspend fun supportRange(
        request: DownloadRequest,
        config: DownloadConfig
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val headReq = Request.Builder()
                .url(request.url)
                .head()
                .apply { request.headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val client = resolveClient(request)
            val response = client.newCall(headReq).execute()
            response.use { resp ->
                val acceptRanges = resp.header("Accept-Ranges")
                if (acceptRanges == "bytes") return@use true
                if (resp.header("Content-Range") != null) return@use true
            }

            val rangeReq = Request.Builder()
                .url(request.url)
                .header("Range", "bytes=0-0")
                .apply { request.headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()
            val rangeResp = client.newCall(rangeReq).execute()
            rangeResp.use { it.code == 206 }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun execute(request: DownloadRequest): DownloadResponse =
        withContext(Dispatchers.IO) {
            val okRequest = Request.Builder()
                .url(request.url)
                .method(request.method, null)
                .apply { request.headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val client = resolveClient(request)
            val response = client.newCall(okRequest).execute()
            val body = response.body

            val headers = buildMap {
                response.headers.forEach { (name, value) -> put(name, value) }
            }

            // HEAD 请求或非成功响应 → 无响应体，直接关闭 response
            if (response.request.method == "HEAD" || !response.isSuccessful) {
                response.close()
                return@withContext DownloadResponse(
                    responseCode = response.code,
                    contentLength = body.contentLength(),
                    body = null,
                    headers = headers,
                )
            }

            // 返回 Okio BufferedSource，close 时自动关闭 OkHttp Response
            val delegate = body.source()
            val closeableSource = object : ForwardingSource(delegate) {
                override fun close() {
                    try { super.close() } finally { response.close() }
                }
            }.buffer()

            DownloadResponse(
                responseCode = response.code,
                contentLength = body.contentLength(),
                body = closeableSource,
                headers = headers,
            )
        }

    /**
     * 解析 OkHttp Client —— 如果超时参数与默认值不同，创建临时客户端。
     */
    private fun resolveClient(request: DownloadRequest): OkHttpClient {
        val base = customClient ?: defaultClient
        return if (request.connectTimeout == DEFAULT_CONNECT_TIMEOUT &&
            request.readTimeout == DEFAULT_READ_TIMEOUT
        ) {
            base
        } else {
            base.newBuilder()
                .connectTimeout(request.connectTimeout.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(request.readTimeout.toLong(), TimeUnit.MILLISECONDS)
                .build()
        }
    }

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT = 15_000
        private const val DEFAULT_READ_TIMEOUT = 15_000
    }
}
