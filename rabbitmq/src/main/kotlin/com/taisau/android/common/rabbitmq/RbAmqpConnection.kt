package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import kotlinx.coroutines.flow.Flow

interface RbAmqpConnection {
	/** 发布消息到指定 exchange，返回 Publisher Confirms 的 Flow */
	fun publish(
		exchange: String,
		routingKey: String,
		body: ByteArray,
		properties: AMQP.BasicProperties? = null
	): Flow<PubAck>
	
	/** 消费队列，返回消息流（需手动 ack 时使用） */
	fun consume(queue: String, autoAck: Boolean = false): Flow<RbAmqpMessage>
	
	/** 手动 ack 消息 */
	suspend fun ack(deliveryTag: Long, multiple: Boolean = false)
	
	/** 手动 nack 消息 */
	suspend fun nack(deliveryTag: Long, multiple: Boolean = false, requeue: Boolean = true)
	
	/** 断开连接 */
	suspend fun disconnect()
	
	/** 连接状态 */
	val isConnected: Boolean
}