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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class AutoReconnectAmqpConnection(
    private val config: RbAmqpConfig
) : RbAmqpConnection{

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connection: RawAmqpConnection? = null
    private var mutex = Mutex()
    private val _isConnected = MutableStateFlow(false)

    private data class ConsumerInfo(
        val queue: String,
        val autoAck: Boolean,
        val consumerTag: String
    )
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

    override fun consume(
        queue: String,
        autoAck: Boolean
    ): Flow<RbAmqpMessage> = flow {
        val consumerTag = "consumer-${UUID.randomUUID()}"
        activeConsumers[consumerTag] = ConsumerInfo(queue, autoAck, consumerTag)

        //如果当前已连接，立即开始消费
        connection?.startConsumer(queue, autoAck, consumerTag)

        //转发消息
        emitAll(connection!!.massageFlow.filter { msg ->
            msg.envelope.deliveryTag > 0
        })
    }.flowOn(Dispatchers.IO).onCompletion {
        activeConsumers.remove(consumerTag)
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


    private suspend fun awaitConnection(): RawAmqpConnection {
        if (_isConnected.value) return connection!!
        _isConnected.first{ it }
        return connection!!
    }

    fun start(){
        scope.launch {
            var attempt = 0
            while (isActive && attempt < config.maxReconnectAttempts){
                try {
                    val raw = RawAmqpConnection(config)
                    raw.connect()

                    // 恢复消费者
                    activeConsumers.values.forEach { info ->
                        raw.startConsumer(info.queue, info.autoAck, info.consumerTag)
                    }
                    connection = raw
                    _isConnected.value = true
                    attempt  = 0

                    //
                    raw.connectionShutdown.collect {
                        _isConnected.value = false
                        connection = null

                    }
                } catch (e: Exception) {
                    _isConnected.value = false
                }
                attempt++
                delay(config.reconnectDelayMills.milliseconds)
            }
           _isConnected.value = false
        }
    }

    fun stop(){
        scope.cancel()
    }
}