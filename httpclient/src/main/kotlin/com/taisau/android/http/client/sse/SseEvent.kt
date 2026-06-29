package com.taisau.android.http.client.sse

/**
 * SSE（Server-Sent Events）事件类型。
 *
 * 通过 [Flow] 收集：
 * ```kotlin
 * client.sse("/stream").collect { event ->
 *     when (event) {
 *         is SseEvent.Connecting -> showStatus("连接中…")
 *         is SseEvent.Connected -> showStatus("已连接")
 *         is SseEvent.Data -> handleData(event)
 *         is SseEvent.Error -> handleError(event)
 *         is SseEvent.Disconnected -> showStatus("已断开")
 *     }
 * }
 * ```
 */
sealed interface SseEvent {

    /** 正在连接 */
    data object Connecting : SseEvent

    /** 连接已建立 */
    data object Connected : SseEvent

    /**
     * 收到数据事件。
     * @param data 事件数据（多行 data: 以 \n 拼接）
     * @param eventType 事件类型（event: 字段），默认 "message"
     * @param id 事件 ID（id: 字段），重连时通过 Last-Event-ID 发送
     */
    data class Data(
        val data: String,
        val eventType: String = "message",
        val id: String? = null,
    ) : SseEvent

    /** 连接已断开 */
    data object Disconnected : SseEvent

    /** 发生错误 */
    data class Error(
        val throwable: Throwable,
    ) : SseEvent
}
