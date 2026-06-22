package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConfirmListener
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownSignalException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * RabbitMQ 原始连接封装（低级 API）
 *
 * 管理 AMQP 连接和单个信道，提供消息发布、消费、确认等基础功能。
 * 不包含自动重连逻辑，适用于需要完全控制连接生命周期的场景。
 *
 * @param config RabbitMQ 连接配置
 */
internal class RawAmqpConnection(
    private val config: RbAmqpConfig
) : RbAmqpConnection {

    private var connection: Connection? = null
    private var channel: com.rabbitmq.client.Channel? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 连接关闭事件流 */
    private val _connectionShutdown = MutableSharedFlow<ShutdownSignalException>(extraBufferCapacity = 1)
    val connectionShutdown: SharedFlow<ShutdownSignalException> = _connectionShutdown

    /** 接收到的消息管道 */
    private val _messageChannel = Channel<RbAmqpMessage>(Channel.BUFFERED)
    val messageFlow: SharedFlow<RbAmqpMessage> = _messageChannel.receiveAsFlow()
        .shareIn(scope, SharingStarted.Eagerly)

    /** 发布确认事件流 */
    private val _ackFlow = MutableSharedFlow<PubAck>(extraBufferCapacity = 64)
    val ackFlow: SharedFlow<PubAck> = _ackFlow

    override val isConnected: Boolean
        get() = connection?.isOpen == true && channel?.isOpen == true

    /**
     * 建立与 RabbitMQ 服务器的连接
     */
    suspend fun connect() {
        withContext(Dispatchers.IO) {
            val factory = ConnectionFactory().apply {
                host = config.host
                port = config.port
                virtualHost = config.virtualHost
                username = config.username
                password = config.password
            }
            connection = factory.newConnection()
            channel = connection?.createChannel()

            // 启用发布者确认模式
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

            // 监听连接关闭事件
            connection?.addShutdownListener { cause ->
                scope.launch { _connectionShutdown.emit(cause) }
            }
        }
    }

    /**
     * 发布消息到交换机
     *
     * 如果启用了发布者确认模式，Flow 会在收到确认后发射 PubAck；
     * 否则发射空 Flow。
     *
     * @param exchange 目标交换机名称
     * @param routingKey 路由键
     * @param body 消息体
     * @param properties 消息属性
     * @return 发布确认结果流
     */
    override fun publish(
        exchange: String,
        routingKey: String,
        body: ByteArray,
        properties: AMQP.BasicProperties?
    ): Flow<PubAck> = flow {
        val ch = channel ?: throw AmqpException("Not connected")
        val seqNo = ch.nextPublishSeqNo
        channel?.basicPublish(exchange, routingKey, properties, body)
        if (config.enablePublisherConfirms) {
            val ack = withContext(Dispatchers.IO) {
                ackFlow.first { it.deliveryTag >= seqNo }
            }
            emit(ack)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 启动消费者并返回消息流
     *
     * @param queue 队列名称
     * @param autoAck 是否自动确认
     * @param consumerTag 消费者标签
     */
    suspend fun startConsumer(
        queue: String,
        autoAck: Boolean,
        consumerTag: String
    ) {
        withContext(Dispatchers.IO) {
            val ch = channel ?: throw AmqpException("Not connected")
            ch.basicConsume(queue, autoAck, consumerTag, object : DefaultConsumer(ch) {
                override fun handleDelivery(
                    consumerTag: String?,
                    envelope: Envelope?,
                    properties: AMQP.BasicProperties?,
                    body: ByteArray?
                ) {
                    envelope ?: return
                    val msg = RbAmqpMessage(
                        body ?: byteArrayOf(),
                        envelope,
                        properties ?: AMQP.BasicProperties.Builder().build()
                    )
                    scope.launch { _messageChannel.send(msg) }
                }
            })
        }
    }

    /**
     * 消费队列消息（Flow 方式）
     *
     * @param queue 队列名称
     * @param autoAck 是否自动确认（true 时消息被自动确认）
     * @return 消息流
     */
    override fun consume(
        queue: String,
        autoAck: Boolean
    ): Flow<RbAmqpMessage> = flow {
        val consumerTag = "consumer-${UUID.randomUUID()}"
        startConsumer(queue, autoAck, consumerTag)
        emitAll(messageFlow)
    }.flowOn(Dispatchers.IO)

    override suspend fun ack(deliveryTag: Long, multiple: Boolean) {
        channel?.basicAck(deliveryTag, multiple)
    }

    override suspend fun nack(deliveryTag: Long, multiple: Boolean, requeue: Boolean) {
        channel?.basicNack(deliveryTag, multiple, requeue)
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                channel?.close()
            } catch (_: Exception) { /* ignore */ }
            try {
                connection?.close()
            } catch (_: Exception) { /* ignore */ }
        }
    }
}
