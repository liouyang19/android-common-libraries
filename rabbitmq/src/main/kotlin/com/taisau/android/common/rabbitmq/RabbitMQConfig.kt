package com.taisau.android.common.rabbitmq

/**
 * RabbitMQ 连接配置（经典 API）
 *
 * @property host 服务器主机地址，默认 localhost
 * @property port 服务器端口，默认 5672
 * @property username 用户名，默认 guest
 * @property password 密码，默认 guest
 * @property virtualHost 虚拟主机，默认 /
 * @property connectionTimeout 连接超时（毫秒），默认 30000
 * @property handshakeTimeout 握手超时（毫秒），默认 10000
 * @property automaticRecovery 是否启用自动恢复，默认 true
 * @property networkRecoveryInterval 网络恢复间隔（毫秒），默认 5000
 * @property requestedHeartbeat 请求心跳间隔（秒），默认 60
 */
data class RabbitMQConfig(
    val host: String = "localhost",
    val port: Int = 5672,
    val username: String = "guest",
    val password: String = "guest",
    val virtualHost: String = "/",
    val connectionTimeout: Int = 30000,
    val handshakeTimeout: Int = 10000,
    val automaticRecovery: Boolean = true,
    val networkRecoveryInterval: Long = 5000,
    val requestedHeartbeat: Int = 60
)
