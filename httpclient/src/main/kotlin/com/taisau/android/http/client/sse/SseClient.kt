package com.taisau.android.http.client.sse

import com.taisau.android.http.client.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * SSE 配置。
 *
 * ```kotlin
 * client.sse("/events") {
 *     autoReconnect = true
 *     retryDelay = 3000L
 *     lastEventId = "last-id-123"
 *     headers["Authorization"] = "Bearer token"
 * }
 * ```
 */
data class SseConfig(
    /** 连接断开后是否自动重连，默认 true */
    val autoReconnect: Boolean = true,
    /** 重连延迟（毫秒），默认 3000ms。服务端可通过 `retry:` 字段覆盖此值 */
    var retryDelay: Long = 3000L,
    /** 初始 Last-Event-ID，重连时自动携带 */
    val initialLastEventId: String? = null,
    /** 自定义请求头 */
    val headers: MutableMap<String, String> = mutableMapOf(),
) {
    /** 记录最新的 event id，用于重连 */
    internal var lastEventId: String? = initialLastEventId
        private set

    internal fun updateLastEventId(id: String) {
        lastEventId = id
    }
}

/**
 * 基于 [ApiClient] 创建 SSE 连接，返回 [Flow]。
 *
 * 底层使用 OkHttp 官方的 [EventSources] 解析 SSE 协议，无需手动处理流解析。
 *
 * 该 Flow 是 **冷流** — 在 collect 时建立连接，在取消 collect 时断开连接。
 * 支持自动重连，断开后按 [SseConfig.retryDelay] 延迟重新连接。
 *
 * ## 使用示例
 *
 * ```kotlin
 * val client = ApiClient {
 *     baseUrl("https://api.example.com")
 * }
 *
 * client.sse("/events").collect { event ->
 *     when (event) {
 *         is SseEvent.Data -> handle(event.data)
 *         is SseEvent.Error -> logError(event.throwable)
 *         else -> {}
 *     }
 * }
 *
 * // 带配置
 * client.sse("/stream") {
 *     autoReconnect = true
 *     retryDelay = 5000L
 *     headers["Authorization"] = "Bearer $token"
 * }.catch { e ->
 *     log("SSE error: $e")
 * }.launchIn(lifecycleScope)
 * ```
 *
 * @param path API 路径（如 "/events"），与 [ApiClient.baseUrl] 拼接
 * @param config 可选配置
 * @return cold Flow，collect 时连接，取消时断开
 */
fun ApiClient.sse(
    path: String,
    config: SseConfig.() -> Unit = {},
): Flow<SseEvent> = callbackFlow {
    val sseConfig = SseConfig().apply(config)
    val okHttpClient = buildSseOkHttpClient(this@sse)
    val eventSourceFactory = EventSources.createFactory(okHttpClient)

    var currentSource: EventSource? = null
    var reconnectJob: Job? = null

    // 前向声明，解决 scheduleReconnectIfNeeded ⇄ startConnection 互相调用的问题
    lateinit var startConnection: () -> Unit

    fun scheduleReconnectIfNeeded() {
        if (!sseConfig.autoReconnect || !isActive) return
        reconnectJob = launch {
            delay(sseConfig.retryDelay.milliseconds)
            if (isActive) startConnection()
        }
    }

    startConnection = {
        val url = "${this@sse.baseUrl.trimEnd('/')}$path"
        val request = buildSseRequest(url, sseConfig)

        trySend(SseEvent.Connecting)

        currentSource = eventSourceFactory.newEventSource(request, object : EventSourceListener() {

            override fun onOpen(eventSource: EventSource, response: Response) {
                trySend(SseEvent.Connected)
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (!id.isNullOrEmpty()) {
                    sseConfig.updateLastEventId(id)
                }
                trySend(SseEvent.Data(
                    data = data,
                    eventType = type ?: "message",
                    id = sseConfig.lastEventId,
                ))
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(SseEvent.Disconnected)
                scheduleReconnectIfNeeded()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                val error = when {
                    t != null -> t
                    response != null -> IOException("SSE HTTP ${response.code}")
                    else -> IOException("SSE unknown error")
                }
                trySend(SseEvent.Error(error))
                scheduleReconnectIfNeeded()
            }
        })
    }

    // 启动首次连接
    startConnection()

    // Flow 取消时清理
    awaitClose {
        currentSource?.cancel()
        reconnectJob?.cancel()
    }
}

// ──────────────────────────────────────────────
// 内部实现
// ──────────────────────────────────────────────

/** 构建 SSE 专用的 OkHttpClient */
private fun buildSseOkHttpClient(client: ApiClient): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(client.config.connectTimeout, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)  // SSE 长连接，永不超时
        .writeTimeout(client.config.writeTimeout, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

/** 构建 SSE 请求 */
private fun buildSseRequest(url: String, config: SseConfig): Request {
    return Request.Builder()
        .url(url)
        .header("Accept", "text/event-stream")
        .header("Cache-Control", "no-cache")
        .apply {
            config.headers.forEach { (k, v) -> header(k, v) }
            config.lastEventId?.let { header("Last-Event-ID", it) }
        }
        .build()
}
