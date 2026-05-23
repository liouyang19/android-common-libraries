package com.taisau.android.common.rabbitmq


import com.rabbitmq.client.AMQP
import kotlinx.coroutines.channels.Channel
import com.rabbitmq.client.ConfirmListener
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownSignalException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RawAmqpConnection(
    private val config: RbAmqpConfig
) {
    private var connection: Connection? = null
    private var channel: com.rabbitmq.client.Channel? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionShutdown = MutableSharedFlow<ShutdownSignalException>(extraBufferCapacity = 1)
    val connectionShutdown: SharedFlow<ShutdownSignalException> = _connectionShutdown

    private val _messageChannel = Channel<RbAmqpMessage>(Channel.BUFFERED)
    val massageFlow: SharedFlow<RbAmqpMessage> = _messageChannel.receiveAsFlow()
        .shareIn(scope, SharingStarted.Eagerly)

    private val _ackFlow = MutableSharedFlow<PubAck>(extraBufferCapacity = 64)
    val ackFlow: SharedFlow<PubAck> = _ackFlow

    val isConnected: Boolean
        get() = connection?.isOpen == true && channel?.isOpen == true

    suspend fun connect() = withContext(Dispatchers.IO){
        val factory = ConnectionFactory().apply {
            host = config.host
            port = config.port
            virtualHost = config.virtualHost
            username = config.username
            password = config.password
        }
        connection = factory.newConnection()
        channel = connection?.createChannel()

        if (config.enablePublisherConfirms){
            channel?.confirmSelect()
            channel?.addConfirmListener(object : ConfirmListener {
                override fun handleAck(deliveryTag: Long, multiple: Boolean) {
                    scope.launch { _ackFlow.emit(PubAck(deliveryTag, multiple, true))}
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

    suspend fun publish(
        exchange: String,
        routingKey: String,
        body: ByteArray,
        props: AMQP.BasicProperties? = null
    ): PubAck? {
        val ch = channel ?: throw AmqpExeception("Not connected")
        val seqNo = ch.nextPublishSeqNo
        channel?.basicPublish(exchange, routingKey, props, body)
        return if (config.enablePublisherConfirms) {
            //阻塞等待确认
            withContext(Dispatchers.IO){
                //等待确认
                ackFlow.first { it.deliveryTag >= seqNo }
            }
        } else {
            null
        }
    }
    suspend fun startConsumer(
        queue: String,
        autoAck: Boolean,
        consumerTag: String
    ) = withContext(Dispatchers.IO) {
        val ch = channel ?: throw AmqpExeception("Not connected")
        ch.basicConsume(queue, autoAck, consumerTag, object : DefaultConsumer(ch){
            override fun handleDelivery(
                consumerTag: String?,
                envelope: Envelope?,
                properties: AMQP.BasicProperties?,
                body: ByteArray?
            ) {
                envelope?: return
                val msg = RbAmqpMessage(
                    body ?: byteArrayOf(),
                    envelope,
                    properties ?: AMQP.BasicProperties.Builder().build()
                )
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

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        channel?.close()
        connection?.close()
    }
}