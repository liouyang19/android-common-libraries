package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.MessageProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 发布消息 DSL
class RabbitMQPublisher(private val client: RabbitMQClient) {

    private var channelName: String = "default"
    private var exchange: String? = null
    private var routingKey: String = ""
    private var persistent: Boolean = true
    private var confirmMode: Boolean = false

    fun channel(name: String): RabbitMQPublisher {
        this.channelName = name
        return this
    }

    fun toExchange(exchange: String, routingKey: String = ""): RabbitMQPublisher {
        this.exchange = exchange
        this.routingKey = routingKey
        return this
    }

    fun toQueue(queueName: String): RabbitMQPublisher {
        this.routingKey = queueName
        return this
    }

    fun persistent(value: Boolean = true): RabbitMQPublisher {
        this.persistent = value
        return this
    }

    fun withConfirm(): RabbitMQPublisher {
        this.confirmMode = true
        return this
    }

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