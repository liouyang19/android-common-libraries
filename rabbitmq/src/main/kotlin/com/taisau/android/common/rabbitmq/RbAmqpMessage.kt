package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Envelope


data class RbAmqpMessage(
    val body: ByteArray,
    val envelope: Envelope,
    val properties: AMQP.BasicProperties,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RbAmqpMessage

        if (!body.contentEquals(other.body)) return false
        if (envelope != other.envelope) return false
        if (properties != other.properties) return false

        return true
    }

    override fun hashCode(): Int {
        var result = body.contentHashCode()
        result = 31 * result + envelope.hashCode()
        result = 31 * result + properties.hashCode()
        return result
    }
}