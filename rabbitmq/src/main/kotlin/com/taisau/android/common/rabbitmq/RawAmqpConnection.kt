package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.ShutdownSignalException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

internal class RawAmqpConnection(private val config: RbAmqpConfig) {
	private var connection: com.rabbitmq.client.Connection? = null
	private var channel: com.rabbitmq.client.Channel? = null
	private val scope = CoroutineScope(IO + SupervisorJob())
	
	private val _connectionShutdown = MutableSharedFlow<ShutdownSignalException>(extraBufferCapacity = 1)
	val connectionShutdown: SharedFlow<ShutdownSignalException> = _connectionShutdown
	
	private val _messageChannel = Channel<RbAmqpMessage>(Channel.BUFFERED)
	val messageFlow: SharedFlow<RbAmqpMessage> = _messageChannel.receiveAsFlow().shareIn(scope, SharingStarted.Eagerly)
	
	private val _ackFlow = MutableSharedFlow<PubAck>(extraBufferCapacity = 64)
	val ackFlow: SharedFlow<PubAck> = _ackFlow
	
	val isConnected: Boolean get() = connection?.isOpen == true && channel?.isOpen == true
	
	suspend fun connect() = withContext(IO) {
		val factory = ConnectionFactory().apply {
			host = config.host
			port = config.port
			virtualHost = config.virtualHost
			username = config.username
			password = config.password
		}
		connection = factory.newConnection(config.connectionName)
		channel = connection?.createChannel()
		
		if (config.enablePublisherConfirms) {
			channel?.confirmSelect()
			channel?.addConfirmListener(object : ConfirmListener {
				override fun handleAck(deliveryTag: Long, multiple: Boolean) {
					scope.launch { _ackFlow.emit(PubAck(deliveryTag, multiple, true)) }
				}
				override fun handleNack(deliveryTag: Long, multiple: Boolean) {
					scope.launch { _ackFlow.emit(PubAck(deliveryTag, multiple, false)) }
				}
			})
		}
		
		connection?.addShutdownListener { cause ->
			scope.launch { _connectionShutdown.emit(cause) }
		}
	}
	
	suspend fun publish(exchange: String, routingKey: String, body: ByteArray, props: AMQP.BasicProperties?): PubAck? {
		val ch = channel ?: throw AmqpException("Not connected")
		val seqNo = ch.nextPublishSeqNo
		ch.basicPublish(exchange, routingKey, props, body)
		// 如果开启 confirms，等待确认；否则立即返回 null
		if (config.enablePublisherConfirms) {
			// 阻塞等待确认
			return withContext(IO) {
				// 等待确认（简化：使用挂起等待 ackFlow 中 deliveryTag >= seqNo）
				ackFlow.first { it.deliveryTag >= seqNo }
			}
		}
		return null
	}
	
	suspend fun startConsumer(queue: String, autoAck: Boolean, consumerTag: String) = withContext(IO) {
		val ch = channel ?: throw AmqpException("Not connected")
		ch.basicConsume(queue, autoAck, consumerTag, object : DefaultConsumer(ch) {
			override fun handleDelivery(
				consumerTag: String?,
				envelope: Envelope?,
				properties: AMQP.BasicProperties?,
				body: ByteArray?
			) {
				envelope ?: return
				val msg = RbAmqpMessage(body ?: ByteArray(0), envelope, properties ?: AMQP.BasicProperties.Builder().build())
				scope.launch { _messageChannel.send(msg) }
			}
		})
	}
	
	suspend fun ack(deliveryTag: Long, multiple: Boolean) {
		channel?.basicAck(deliveryTag, multiple)
	}
	
	suspend fun nack(deliveryTag: Long, multiple: Boolean, requeue: Boolean) {
		channel?.basicNack(deliveryTag, multiple, requeue)
	}
	
	suspend fun disconnect() = withContext(IO) {
		channel?.close()
		connection?.close()
	}
}