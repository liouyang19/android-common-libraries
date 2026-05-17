package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// RabbitMQClient.kt
class RabbitMQClient private constructor(
    private val config: RabbitMQConfig
) {
    private val connectionFactory = ConnectionFactory().apply {
        host = config.host
        port = config.port
        username = config.username
        password = config.password
        virtualHost = config.virtualHost
        connectionTimeout = config.connectionTimeout
        handshakeTimeout = config.handshakeTimeout
        isAutomaticRecoveryEnabled = config.automaticRecovery
        networkRecoveryInterval = config.networkRecoveryInterval
        requestedHeartbeat = config.requestedHeartbeat
    }

    private var connection: Connection? = null
    private val channels = ConcurrentHashMap<String, Channel>()

    suspend fun connect(): Result<Connection> = withContext(Dispatchers.IO) {
        try {
            connection = connectionFactory.newConnection()
            Result.success(connection!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getOrCreateChannel(name: String): Result<Channel> {
        return try {
            val channel = channels.getOrPut(name) {
                connection?.createChannel()
                    ?: throw IllegalStateException("Connection not established")
            }
            Result.success(channel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        channels.values.forEach { it.close() }
        channels.clear()
        connection?.close()
    }

    class Builder {
        private var config = RabbitMQConfig()

        fun host(host: String) = apply { config = config.copy(host = host) }
        fun port(port: Int) = apply { config = config.copy(port = port) }
        fun credentials(username: String, password: String) = apply {
            config = config.copy(username = username, password = password)
        }
        fun virtualHost(vhost: String) = apply { config = config.copy(virtualHost = vhost) }
        fun timeouts(connection: Int = 30000, handshake: Int = 10000) = apply {
            config = config.copy(connectionTimeout = connection, handshakeTimeout = handshake)
        }
        fun automaticRecovery(enabled: Boolean = true, interval: Long = 5000) = apply {
            config = config.copy(automaticRecovery = enabled, networkRecoveryInterval = interval)
        }

        suspend fun build(): RabbitMQClient {
            val client = RabbitMQClient(config)
            client.connect().getOrThrow()
            return client
        }
    }

    companion object {
        fun create(block: Builder.() -> Unit): Builder {
            return Builder().apply(block)
        }
    }
}