package com.taisau.android.common.rabbitmq

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

internal class AutoReconnectAmqpConnection(
	private val config: RbAmqpConfig
) : RbAmqpConnection {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var inner: RawAmqpConnection? = null
	private val mutex = Mutex()
	private val _isConnected = MutableStateFlow(false)
	
	// 保存活跃的消费者信息，用于恢复
	private data class ConsumerInfo(val queue: String, val autoAck: Boolean, val consumerTag: String)
	private val activeConsumers = ConcurrentHashMap<String, ConsumerInfo>()
	
	override val isConnected: Boolean get() = _isConnected.value
	
	override fun publish(
		exchange: String,
		routingKey: String,
		body: ByteArray,
		properties: AMQP.BasicProperties?
	): Flow<PubAck> = flow {
		val conn = awaitConnection()
		val ack = conn.publish(exchange, routingKey, body, properties)
		if (ack != null) emit(ack)
	}.flowOn(Dispatchers.IO)
	
	override fun consume(queue: String, autoAck: Boolean): Flow<RbAmqpMessage> = flow {
		val consumerTag = "consumer-${UUID.randomUUID()}"
		activeConsumers[consumerTag] = ConsumerInfo(queue, autoAck, consumerTag)
		
		// 如果当前已连接，立即开始消费
		inner?.let { it.startConsumer(queue, autoAck, consumerTag) }
		
		// 转发消息
		emitAll(inner!!.messageFlow.filter { msg ->
			msg.envelope.deliveryTag > 0 // 可根据需要过滤
		})
	}.flowOn(Dispatchers.IO).onCompletion {
		activeConsumers.remove(consumerTag)
	}
	
	override suspend fun ack(deliveryTag: Long, multiple: Boolean) {
		inner?.ack(deliveryTag, multiple)
	}
	
	override suspend fun nack(deliveryTag: Long, multiple: Boolean, requeue: Boolean) {
		inner?.nack(deliveryTag, multiple, requeue)
	}
	
	override suspend fun disconnect() {
		scope.cancel()
		inner?.disconnect()
	}
	
	private suspend fun awaitConnection(): RawAmqpConnection {
		if (_isConnected.value) return inner!!
		_isConnected.first { it }
		return inner!!
	}
	
	fun start() {
		scope.launch {
			var attempt = 0
			while (isActive && attempt < config.maxReconnectAttempts) {
				try {
					val raw = RawAmqpConnection(config)
					raw.connect()
					
					// 恢复消费者
					activeConsumers.values.forEach { info ->
						raw.startConsumer(info.queue, info.autoAck, info.consumerTag)
					}
					
					inner = raw
					_isConnected.value = true
					attempt = 0
					
					// 等待连接关闭信号
					raw.connectionShutdown.collect { cause ->
						_isConnected.value = false
						inner = null
						// 继续外层循环重连
					}
				} catch (e: Exception) {
					_isConnected.value = false
				}
				attempt++
				delay(config.reconnectDelayMillis)
			}
			_isConnected.value = false
		}
	}
	
	fun close() {
		scope.cancel()
	}
}