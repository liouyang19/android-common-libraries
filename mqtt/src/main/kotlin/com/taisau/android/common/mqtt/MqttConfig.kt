package com.taisau.android.common.mqtt

data class MqttConfig(
    val clientId:  String,
    val username: String? = null,
    val password: String? = null,
    val cleanSession: Boolean = true,
    val keepAliveInterval: Int = 60,
    val connectionTimeout: Int = 30,
    val sslConfig:SslConfig? = null,

)
