package com.taisau.android.common.rabbitmq

/**
 * RabbitMQ 连接配置（响应式 API）
 *
 * @property host 服务器主机地址，默认 localhost
 * @property port 服务器端口，默认 5672
 * @property virtualHost 虚拟主机，默认 /
 * @property username 用户名
 * @property password 密码
 * @property connectionName 连接名称，默认 android-{时间戳}
 * @property enablePublisherConfirms 是否启用发布者确认模式，默认 true
 * @property autoReconnect 是否启用自动重连，默认 true
 * @property reconnectDelayMills 重连延迟时间（毫秒），默认 2000ms
 * @property maxReconnectAttempts 最大重连次数，默认 Int.MAX_VALUE
 * @property heartbeat 心跳间隔（秒），默认 60s
 */
data class RbAmqpConfig(
    val host: String = "localhost",
    val port: Int = 5672,
    val virtualHost: String = "/",
    val username: String,
    val password: String,
    val connectionName: String = "android-${System.currentTimeMillis()}",
    val enablePublisherConfirms: Boolean = true,
    val autoReconnect: Boolean = true,
    val reconnectDelayMills: Long = 2000L,
    val maxReconnectAttempts: Int = Int.MAX_VALUE,
    val heartbeat: Int = 60
)
