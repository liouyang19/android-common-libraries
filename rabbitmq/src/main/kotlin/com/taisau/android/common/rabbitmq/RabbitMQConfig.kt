package com.taisau.android.common.rabbitmq

// RabbitMQConfig.kt
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
