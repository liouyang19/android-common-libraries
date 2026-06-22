package com.taisau.android.common.rabbitmq

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
