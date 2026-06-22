package com.taisau.android.common.rabbitmq

/**
 * 发布确认结果
 *
 * 当启用发布者确认模式时，RabbitMQ 服务器会为每条发布的消息返回确认结果。
 *
 * @property deliveryTag 投递标签（唯一标识已发布的消息）
 * @property multiple 是否批量确认（true 表示确认此标签之前的所有消息）
 * @property success 是否成功
 * @property error 错误信息（仅 success 为 false 时有效）
 */
data class PubAck(
    val deliveryTag: Long,
    val multiple: Boolean,
    val success: Boolean,
    val error: Throwable? = null
)
