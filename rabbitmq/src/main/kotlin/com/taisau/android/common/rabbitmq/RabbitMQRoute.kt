package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 消息路由 DSL
class RabbitMQRoute(private val client: RabbitMQClient) {

    private var channelName: String = "default"
    private var exchangeDeclaration: ExchangeDeclaration? = null
    private var queueBinding: QueueBinding? = null
    private var autoAck: Boolean = false
    private var prefetchCount: Int = 1
    private var onMessage: (suspend (Message) -> Unit)? = null

    fun channel(name: String): RabbitMQRoute {
        this.channelName = name
        return this
    }

    fun exchange(name: String, type: ExchangeType, block: ExchangeDeclaration.() -> Unit = {}): RabbitMQRoute {
        this.exchangeDeclaration = ExchangeDeclaration(name, type).apply(block)
        return this
    }

    fun queue(name: String, block: QueueBinding.() -> Unit = {}): RabbitMQRoute {
        this.queueBinding = QueueBinding(name).apply(block)
        return this
    }

    fun bindTo(exchange: String, routingKey: String = ""): RabbitMQRoute {
        this.queueBinding = this.queueBinding?.copy(exchange = exchange, routingKey = routingKey)
        return this
    }

    fun autoAcknowledge(): RabbitMQRoute {
        this.autoAck = true
        return this
    }

    fun prefetch(count: Int): RabbitMQRoute {
        this.prefetchCount = count
        return this
    }

    fun onMessage(block: suspend (Message) -> Unit): RabbitMQRoute {
        this.onMessage = block
        return this
    }

    // 启动消费者
    suspend fun consume(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val channel = client.getOrCreateChannel(channelName).getOrThrow()

            // 声明交换机
            exchangeDeclaration?.let {
                channel.exchangeDeclare(it.name, it.type.toAmqpType(), it.durable, it.autoDelete, null)
            }

            // 声明队列
            val queue = queueBinding?.let {
                channel.queueDeclare(it.queue, it.durable, it.exclusive, it.autoDelete, null)

                // 绑定队列到交换机
                if (it.exchange != null) {
                    channel.queueBind(it.queue, it.exchange, it.routingKey)
                }

                it.queue
            } ?: throw IllegalStateException("Queue binding not defined")

            // 设置 prefetch
            channel.basicQos(prefetchCount)

            // 创建消费者
            val consumer = object : DefaultConsumer(channel) {
                override fun handleDelivery(
                    consumerTag: String?,
                    envelope: Envelope?,
                    properties: AMQP.BasicProperties?,
                    body: ByteArray?
                ) {
                    val message = Message(
                        body = body ?: ByteArray(0),
                        envelope = envelope,
                        properties = properties,
                        consumerTag = consumerTag
                    )

                    // 在协程中处理消息
                    CoroutineScope(Dispatchers.IO).launch {
                        onMessage?.invoke(message)

                        if (!autoAck) {
                            channel.basicAck(envelope?.deliveryTag ?: 0, false)
                        }
                    }
                }
            }

            channel.basicConsume(queue, autoAck, consumer)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}