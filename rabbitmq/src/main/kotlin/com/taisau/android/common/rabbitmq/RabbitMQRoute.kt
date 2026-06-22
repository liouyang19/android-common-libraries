package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 消息路由/消费者（经典 API，DSL 模式）
 *
 * 使用 DSL 风格配置消费参数，支持：
 * - 交换机声明和绑定
 * - 队列声明和绑定
 * - 手动/自动确认
 * - prefetch 设置
 * - 协程消息处理
 *
 * 使用示例：
 * ```
 * RabbitMQRoute(client)
 *     .exchange("logs", ExchangeType.Fanout)
 *     .queue("my_queue")
 *     .bindTo("logs")
 *     .prefetch(10)
 *     .onMessage { msg ->
 *         println("收到: ${msg.bodyAsString()}")
 *     }
 *     .consume()
 * ```
 */
class RabbitMQRoute(private val client: RabbitMQClient) {

    private var channelName: String = "default"
    private var exchangeDeclaration: ExchangeDeclaration? = null
    private var queueBinding: QueueBinding? = null
    private var autoAck: Boolean = false
    private var prefetchCount: Int = 1
    private var onMessage: (suspend (Message) -> Unit)? = null

    /**
     * 指定使用的信道名称
     */
    fun channel(name: String): RabbitMQRoute {
        this.channelName = name
        return this
    }

    /**
     * 配置交换机声明
     *
     * @param name 交换机名称
     * @param type 交换机类型
     * @param block 交换机声明配置 DSL
     */
    fun exchange(name: String, type: ExchangeType, block: ExchangeDeclaration.() -> Unit = {}): RabbitMQRoute {
        this.exchangeDeclaration = ExchangeDeclaration(name, type).apply(block)
        return this
    }

    /**
     * 配置队列声明
     *
     * @param name 队列名称
     * @param block 队列绑定配置 DSL
     */
    fun queue(name: String, block: QueueBinding.() -> Unit = {}): RabbitMQRoute {
        this.queueBinding = QueueBinding(name).apply(block)
        return this
    }

    /**
     * 将队列绑定到交换机
     *
     * @param exchange 交换机名称
     * @param routingKey 路由键
     */
    fun bindTo(exchange: String, routingKey: String = ""): RabbitMQRoute {
        this.queueBinding = this.queueBinding?.copy(exchange = exchange, routingKey = routingKey)
        return this
    }

    /**
     * 启用自动确认模式
     */
    fun autoAcknowledge(): RabbitMQRoute {
        this.autoAck = true
        return this
    }

    /**
     * 设置 prefetch 数量
     *
     * @param count 每次预取的消息数
     */
    fun prefetch(count: Int): RabbitMQRoute {
        this.prefetchCount = count
        return this
    }

    /**
     * 设置消息处理回调
     *
     * @param block 消息处理协程函数
     */
    fun onMessage(block: suspend (Message) -> Unit): RabbitMQRoute {
        this.onMessage = block
        return this
    }

    /**
     * 启动消费者
     *
     * 声明交换机、队列和绑定关系，然后开始消费消息。
     */
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
            } ?: throw IllegalStateException("未定义队列绑定")

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
