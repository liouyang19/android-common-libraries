package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import kotlinx.coroutines.flow.Flow

interface RbAmqpConnection {

    fun publish(
        exchange: String,
        routingKey: String,
        body: ByteArray,
        properties: AMQP.BasicProperties? = null
    ): Flow<PubAck>

    /**消费队列，返回消息流（需要手动ack时使用）*/
    fun consume(queue: String,autoAck: Boolean = false): Flow<RbAmqpMessage>

    /**手动ack消息*/
    suspend fun ack(deliveryTag: Long, multiple: Boolean)


    /**手动nack消息*/
    suspend fun nack(deliveryTag: Long, multiple: Boolean, requeue: Boolean)

    suspend fun disconnect()

    /** 连接状态 */
    val isConnected: Boolean
}