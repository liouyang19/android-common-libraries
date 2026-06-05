package com.taisau.android.common.rabbitmq

data class RbAmqpConfig(
	val host: String = "localhost",
	val port: Int = 5672,
	val virtualHost: String = "/",
	val username: String = "guest",
	val password: String = "guest",
	val connectionName: String = "android-${System.currentTimeMillis()}",
	val enablePublisherConfirms: Boolean = true,
	// 自动重连配置
	val autoReconnect: Boolean = false,
	val reconnectDelayMillis: Long = 2000L,
	val maxReconnectAttempts: Int = Int.MAX_VALUE
)