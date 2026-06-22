package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.MessageProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 消息发布器（经典 API，DSL 模式）
 *
 * 使用 DSL 风格配置发布参数，支持：
 * - 发布到交换机或直接到队列
 * - 消息持久化
 * - 发布者确认模式
 * - 字符串和字节数组消息体
 *
 * 使用示例：
 * ```
 * val publisher = RabbitMQPublisher(client)
 * publisher.toQueue("task_queue")
 *     .persistent()
 *     .withConfirm()
 *     .publish("Hello World")
 * ```
 */
class RabbitMQPublisher(private val client: RabbitMQClient) {

    private var channelName: String = "default"
    private var exchange: String? = null
    private var routingKey: String = ""
    private var persistent: Boolean = true
    private var confirmMode: Boolean = false

    /**
     * 指定使用的信道名称
     */
    fun channel(name: String): RabbitMQPublisher {
        this.channelName = name
        return this
    }

    /**
     * 设置目标交换机
     *
     * @param exchange 交换机名称
     * @param routingKey 路由键（可选）
     */
    fun toExchange(exchange: String, routingKey: String = ""): RabbitMQPublisher {
        this.exchange = exchange
        this.routingKey = routingKey
        return this
    }

    /**
     * 设置目标队列
     *
     * @param queueName 队列名称
     */
    fun toQueue(queueName: String): RabbitMQPublisher {
        this.routingKey = queueName
        return this
    }

    /**
     * 设置消息是否持久化
     *
     * @param value true 为持久化（默认）
     */
    fun persistent(value: Boolean = true): RabbitMQPublisher {
        this.persistent = value
        return this
    }

    /**
     * 启用发布者确认模式
     */
    fun withConfirm(): RabbitMQPublisher {
        this.confirmMode = true
        return this
    }

    /**
     * 发布字符串消息
     *
     * @param message 消息内容
     */
    suspend fun publish(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val channel = client.getOrCreateChannel(channelName).getOrThrow()

            if (confirmMode) {
                channel.confirmSelect()
            }

            val props = MessageProperties.PERSISTENT_TEXT_PLAIN.takeIf { persistent }
                ?: MessageProperties.TEXT_PLAIN

            channel.basicPublish(
                exchange ?: "",
                routingKey,
                props,
                message.toByteArray()
            )

            if (confirmMode) {
                channel.waitForConfirmsOrDie(5000)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 发布二进制消息
     *
     * @param message 消息体
     * @param contentType 内容类型，默认 application/octet-stream
     */
    suspend fun publish(message: ByteArray, contentType: String = "application/octet-stream"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val channel = client.getOrCreateChannel(channelName).getOrThrow()

            if (confirmMode) {
                channel.confirmSelect()
            }

            val props = AMQP.BasicProperties.Builder()
                .contentType(contentType)
                .deliveryMode(if (persistent) 2 else 1)
                .build()

            channel.basicPublish(
                exchange ?: "",
                routingKey,
                props,
                message
            )

            if (confirmMode) {
                channel.waitForConfirmsOrDie(5000)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
