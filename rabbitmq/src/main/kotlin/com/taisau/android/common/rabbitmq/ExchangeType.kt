package com.taisau.android.common.rabbitmq

sealed class ExchangeType {
    object Direct : ExchangeType()
    object Fanout : ExchangeType()
    object Topic : ExchangeType()
    object Headers : ExchangeType()

    internal fun toAmqpType(): String = when(this) {
        Direct -> "direct"
        Fanout -> "fanout"
        Topic -> "topic"
        Headers -> "headers"
    }
}