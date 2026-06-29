package com.taisau.android.http.client.ws

/**
 * WebSocket 事件（接收方向）。
 *
 * 通过 [WsClient.messages] Flow 收集：
 * ```kotlin
 * client.webSocket("/chat").messages.collect { event ->
 *     when (event) {
 *         is WsEvent.Connected -> println("已连接")
 *         is WsEvent.Text -> handleText(event.text)
 *         is WsEvent.Binary -> handleBinary(event.bytes)
 *         is WsEvent.Error -> logError(event.throwable)
 *         is WsEvent.Disconnected -> println("已断开")
 *     }
 * }
 * ```
 */
sealed interface WsEvent {

    /** 正在连接 */
    data object Connecting : WsEvent

    /** 连接已建立 */
    data object Connected : WsEvent

    /** 收到文本消息 */
    data class Text(val text: String) : WsEvent

    /** 收到二进制消息 */
    data class Binary(val bytes: ByteArray) : WsEvent

    /** 发生错误 */
    data class Error(val throwable: Throwable) : WsEvent

    /** 连接已断开 */
    data object Disconnected : WsEvent
}
