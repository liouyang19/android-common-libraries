package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import kotlinx.coroutines.flow.Flow

/**
 * RabbitMQ 连接接口（响应式 API）
 *
 * 提供基于 Kotlin Flow 的消息发布、消费和确认操作。
 * 支持手动确认（ack/nack）和自动确认模式。
 *
 * @see RawAmqpConnection 原始连接实现
 * @see AutoReconnectAmqpConnection 自动重连连接实现
 */
interface RbAmqpConnection {

    /**
     * 发布消息到交换机
     *
     * @param exchange 目标交换机名称
     * @param routingKey 路由键
     * @param body 消息体
     * @param properties 消息属性（可选）
     * @return [PubAck] 流，仅启用发布者确认时才有数据
     */
    fun publish(
        exchange: String,
        routingKey: String,
        body: ByteArray,
        properties: AMQP.BasicProperties? = null
    ): Flow<PubAck>

    /**
     * 消费队列消息
     *
     * @param queue 队列名称
     * @param autoAck 是否自动确认（true 时不需要手动 ack）
     * @return [RbAmqpMessage] 消息流
     */
    fun consume(queue: String, autoAck: Boolean = false): Flow<RbAmqpMessage>

    /**
     * 手动确认消息
     *
     * @param deliveryTag 消息投递标签
     * @param multiple 是否批量确认
     */
    suspend fun ack(deliveryTag: Long, multiple: Boolean)

    /**
     * 手动拒绝消息
     *
     * @param deliveryTag 消息投递标签
     * @param multiple 是否批量拒绝
     * @param requeue 是否重新入队
     */
    suspend fun nack(deliveryTag: Long, multiple: Boolean, requeue: Boolean)

    /**
     * 断开连接
     */
    suspend fun disconnect()

    /** 当前是否已连接 */
    val isConnected: Boolean
}
