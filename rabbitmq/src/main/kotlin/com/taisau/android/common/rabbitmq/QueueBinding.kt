package com.taisau.android.common.rabbitmq

data class QueueBinding(
    val queue: String,
    val exchange: String? = null,
    val routingKey: String = "",
    val durable: Boolean = true,
    val exclusive: Boolean = false,
    val autoDelete: Boolean = false
)