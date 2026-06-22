package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * 支持自动重连的 RabbitMQ 连接封装
 *
 * 在后台协程中维护连接状态，当连接断开时自动按指数退避策略重连。
 * 重连后自动恢复所有活跃的消费者。
 *
 * @param config RabbitMQ 连接配置
 */
class AutoReconnectAmqpConnection(
    private val config: RbAmqpConfig
) : RbAmqpConnection {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connection: RawAmqpConnection? = null
    private val mutex = Mutex()
    private val _isConnected = MutableStateFlow(false)

    /** 活跃消费者信息，用于重连后自动恢复 */
    private data class ConsumerInfo(
        val queue: String,
        val autoAck: Boolean,
        val consumerTag: String
    )
    private val activeConsumers = ConcurrentHashMap<String, ConsumerInfo>()

    override val isConnected: Boolean get() = _isConnected.value

    /**
     * 发布消息到交换机
     *
     * @return 发布确认结果流（仅启用了发布者确认时才有数据）
     */
    override fun publish(
        exchange: String,
        routingKey: String,
        body: ByteArray,
        properties: AMQP.BasicProperties?
    ): Flow<PubAck> = flow {
        val conn = awaitConnection()
        emitAll(conn.publish(exchange, routingKey, body, properties))
    }.flowOn(Dispatchers.IO)

    /**
     * 消费队列消息
     *
     * 自动注册消费者，断线重连后自动恢复消费。
     * 返回的 Flow 会在取消时自动注销消费者。
     *
     * @param queue 队列名称
     * @param autoAck 是否自动确认
     * @return 消息流
     */
    override fun consume(
        queue: String,
        autoAck: Boolean
    ): Flow<RbAmqpMessage> {
        val consumerTag = "consumer-${UUID.randomUUID()}"
        return flow {
            activeConsumers[consumerTag] = ConsumerInfo(queue, autoAck, consumerTag)

            // 等待连接建立并启动消费者
            val conn = awaitConnection()
            conn.startConsumer(queue, autoAck, consumerTag)

            // 转发消息，连接断开后 Flow 结束
            emitAll(conn.messageFlow)
        }.flowOn(Dispatchers.IO).onCompletion {
            activeConsumers.remove(consumerTag)
        }
    }

    override suspend fun ack(deliveryTag: Long, multiple: Boolean) {
        connection?.ack(deliveryTag, multiple)
    }

    override suspend fun nack(
        deliveryTag: Long,
        multiple: Boolean,
        requeue: Boolean
    ) {
        connection?.nack(deliveryTag, multiple, requeue)
    }

    override suspend fun disconnect() {
        scope.cancel()
        connection?.disconnect()
    }

    /**
     * 等待连接就绪，返回当前有效的原始连接
     */
    private suspend fun awaitConnection(): RawAmqpConnection {
        if (_isConnected.value) return connection!!
        _isConnected.first { it }
        return connection!!
    }

    /**
     * 启动自动重连循环
     *
     * 在后台协程中持续尝试建立连接，连接断开后自动重试。
     */
    fun start() {
        scope.launch {
            var attempt = 0
            while (isActive && attempt < config.maxReconnectAttempts) {
                try {
                    val raw = RawAmqpConnection(config)
                    raw.connect()

                    // 恢复所有活跃消费者
                    mutex.withLock {
                        connection = raw
                        _isConnected.value = true
                    }
                    attempt = 0

                    // 在新连接上启动消费者
                    activeConsumers.values.forEach { info ->
                        raw.startConsumer(info.queue, info.autoAck, info.consumerTag)
                    }

                    // 等待连接关闭
                    raw.connectionShutdown.collect {
                        mutex.withLock {
                            _isConnected.value = false
                            connection = null
                        }
                    }
                } catch (_: Exception) {
                    mutex.withLock {
                        _isConnected.value = false
                    }
                }
                attempt++
                delay(config.reconnectDelayMills.milliseconds)
            }
            _isConnected.value = false
        }
    }

    /**
     * 停止自动重连
     */
    fun stop() {
        scope.cancel()
    }
}
