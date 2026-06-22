package com.taisau.android.common.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Envelope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 消息封装（经典 API）
 *
 * 包含消息体、投递信息和属性，提供便捷的消息解析方法。
 *
 * @property body 消息体（字节数组）
 * @property envelope 投递信息
 * @property properties 消息属性
 * @property consumerTag 消费者标签
 */
data class Message(
    val body: ByteArray,
    val envelope: Envelope?,
    val properties: AMQP.BasicProperties?,
    val consumerTag: String?
) {
    /**
     * 将消息体转为字符串
     */
    fun bodyAsString(): String = String(body)

    /**
     * 将消息体解析为 JSON 对象
     *
     * @return 解析成功返回 [JsonObject]，失败返回 null
     */
    fun bodyAsJson(): JsonObject? = try {
        Json.parseToJsonElement(bodyAsString()).jsonObject
    } catch (_: Exception) {
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
