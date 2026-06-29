package com.taisau.android.http.client.ws

import com.taisau.android.http.client.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * WebSocket 配置。
 *
 * ```kotlin
 * val ws = client.webSocket("/chat") {
 *     autoReconnect = true
 *     reconnectDelay = 5000L
 *     headers["Authorization"] = "Bearer token"
 * }
 * ```
 */
data class WsConfig(
    /** 断开后是否自动重连，默认 true */
    val autoReconnect: Boolean = true,
    /** 重连延迟（毫秒），默认 5000ms */
    var reconnectDelay: Long = 5000L,
    /** 自定义请求头 */
    val headers: MutableMap<String, String> = mutableMapOf(),
)

/**
 * WebSocket 客户端。
 *
 * 通过 [ApiClient.webSocket] 创建，提供：
 * - [messages] Flow — 接收服务器推送的消息
 * - [send] — 发送文本/二进制消息
 * - [close] — 关闭连接
 *
 * 内部使用 OkHttp 的 [WebSocket] + [WebSocketListener]，支持自动重连。
 */
class WsClient internal constructor(
    private val okHttpClient: OkHttpClient,
    private val url: String,
    private val config: WsConfig,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _messages = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    
    /**
     * 接收消息 Flow（热流）。
     *
     * 无论是否有 collector，WebSocket 都会保持连接。
     * 建议在 [lifecycleScope] 或 [viewModelScope] 中收集。
     */
    val messages: SharedFlow<WsEvent> = _messages.asSharedFlow()

    @Volatile
    private var currentWs: WebSocket? = null

    private var reconnectJob: Job? = null

    init {
        connect()
    }

    // ── 发送 ──

    /** 发送文本消息 */
    fun send(text: String): Boolean {
        return currentWs?.send(text) ?: false
    }

    /** 发送二进制消息 */
    fun send(bytes: ByteArray): Boolean {
        return currentWs?.send(bytes.toByteString()) ?: false
    }

    // ── 关闭 ──

    /**
     * 关闭 WebSocket 连接。
     * @param code 关闭状态码，默认 1000 (NORMAL_CLOSURE)
     * @param reason 关闭原因
     */
    fun close(code: Int = 1000, reason: String? = null) {
        reconnectJob?.cancel()
        reconnectJob = null
        currentWs?.close(code, reason)
        currentWs = null
        _messages.tryEmit(WsEvent.Disconnected)
    }

    // ── 内部 ──

    private fun connect() {
        _messages.tryEmit(WsEvent.Connecting)

        val request = Request.Builder()
            .url(url)
            .apply {
                config.headers.forEach { (k, v) -> header(k, v) }
            }
            .build()

        currentWs = okHttpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                _messages.tryEmit(WsEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _messages.tryEmit(WsEvent.Text(text))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                _messages.tryEmit(WsEvent.Binary(bytes.toByteArray()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _messages.tryEmit(WsEvent.Disconnected)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _messages.tryEmit(WsEvent.Error(t))
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!config.autoReconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(config.reconnectDelay.milliseconds)
            if (isActive) connect()
        }
    }
}

// ──────────────────────────────────────────────
// ApiClient 扩展
// ──────────────────────────────────────────────

/**
 * 在 [ApiClient] 基础上创建 WebSocket 连接。
 *
 * 使用示例：
 * ```kotlin
 * val ws = client.webSocket("/ws/chat") {
 *     autoReconnect = true
 *     headers["Authorization"] = "Bearer $token"
 * }
 *
 * // 收集消息
 * ws.messages.collect { event ->
 *     when (event) {
 *         is WsEvent.Text -> handle(event.text)
 *         is WsEvent.Error -> logError(event.throwable)
 *         else -> {}
 *     }
 * }
 *
 * // 发送消息
 * ws.send("Hello!")
 *
 * // 关闭
 * lifecycleScope.launchWhenStarted {
 *     ws.messages.collect { ... }
 * }
 * ```
 *
 * @param path WebSocket 路径（如 "/ws/chat"），与 [ApiClient.baseUrl] 拼接
 * @param config 可选配置
 * @return [WsClient]，通过 [WsClient.messages] 收集事件
 */
fun ApiClient.webSocket(
    path: String,
    config: WsConfig.() -> Unit = {},
): WsClient {
    val wsConfig = WsConfig().apply(config)
    val okHttpClient = buildWsOkHttpClient(this)
    val url = buildWsUrl(this, path)

    return WsClient(okHttpClient, url, wsConfig)
}

/** 构建 WebSocket 专用 OkHttpClient */
private fun buildWsOkHttpClient(client: ApiClient): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(client.config.connectTimeout, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)   // 长连接不超时
        .writeTimeout(0, TimeUnit.MILLISECONDS)  // 长连接不超时
        .retryOnConnectionFailure(true)
        .build()
}

/** 构建 WebSocket URL（http→ws / https→wss） */
private fun buildWsUrl(client: ApiClient, path: String): String {
    val base = client.baseUrl.trimEnd('/')
    val wsBase = base
        .replace("https://", "wss://")
        .replace("http://", "ws://")
    return "$wsBase$path"
}
