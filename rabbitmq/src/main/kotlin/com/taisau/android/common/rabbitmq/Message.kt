package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Envelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class Message(
    val body: ByteArray,
    val envelope: Envelope?,
    val properties: AMQP.BasicProperties?,
    val consumerTag: String?
) {
    fun bodyAsString(): String = String(body)

    fun bodyAsJson(): JsonObject? = try {
        Json.parseToJsonElement(bodyAsString()).jsonObject
    } catch (e: Exception) {
        null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Message
        return body.contentEquals(other.body)
    }

    override fun hashCode(): Int = body.contentHashCode()
}