package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * RabbitMQ 客户端（经典 API，Builder 模式）
 *
 * 管理 AMQP 连接和多个信道，支持连接配置和信道复用。
 * 使用 [Builder] 模式创建，连接在 build 时自动建立。
 *
 * 使用示例：
 * ```
 * val client = RabbitMQClient.create {
 *     host("192.168.1.100")
 *     credentials("admin", "password")
 * }
 * // 发布消息
 * RabbitMQPublisher(client)
 *     .toQueue("my_queue")
 *     .publish("Hello")
 * // 消费消息
 * RabbitMQRoute(client)
 *     .queue("my_queue")
 *     .onMessage { msg -> println(msg.bodyAsString()) }
 *     .consume()
 * ```
 *
 * @see RabbitMQPublisher
 * @see RabbitMQRoute
 */
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

    /**
     * 建立与 RabbitMQ 服务器的连接
     *
     * @return 连接结果
     */
    suspend fun connect(): Result<Connection> = withContext(Dispatchers.IO) {
        try {
            connection = connectionFactory.newConnection()
            Result.success(connection!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取或创建指定名称的信道
     *
     * @param name 信道名称，用于复用信道
     * @return 信道结果
     */
    fun getOrCreateChannel(name: String): Result<Channel> {
        return try {
            val channel = channels.getOrPut(name) {
                connection?.createChannel()
                    ?: throw IllegalStateException("连接未建立")
            }
            Result.success(channel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 关闭连接并释放所有资源
     */
    fun close() {
        channels.values.forEach { it.close() }
        channels.clear()
        connection?.close()
    }

    /**
     * RabbitMQClient 构建器
     *
     * 使用 DSL 方式配置并构建客户端。
     *
     * 示例：
     * ```
     * val client = RabbitMQClient.create {
     *     host("localhost")
     *     port(5672)
     *     credentials("guest", "guest")
     * }
     * ```
     */
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

        /**
         * 构建并自动连接
         *
         * @return 已连接的 [RabbitMQClient] 实例
         */
        suspend fun build(): RabbitMQClient {
            val client = RabbitMQClient(config)
            val _ = client.connect().getOrThrow() // 连接失败则抛出异常
            return client
        }
    }

    companion object {
        /**
         * 使用 DSL 创建 [RabbitMQClient] 构建器
         *
         * @param block 配置 DSL
         * @return [Builder] 实例
         */
        fun create(block: Builder.() -> Unit): Builder {
            return Builder().apply(block)
        }
    }
}
