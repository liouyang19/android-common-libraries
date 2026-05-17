package com.taisau.android.common.rabbitmq

data class ExchangeDeclaration(
    val name: String,
    val type: ExchangeType,
    val durable: Boolean = true,
    val autoDelete: Boolean = false
)