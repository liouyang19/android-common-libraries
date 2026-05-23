package com.taisau.android.common.mqtt

data class MqttMessage(
    val topic: String,
    val payload: ByteArray,
    val qos: Int = 1,
    val retained: Boolean = false,
    val messageId: Int = -1
){

    val payloadString: String
        get() = payload.toString(Charsets.UTF_8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MqttMessage

        if (qos != other.qos) return false
        if (retained != other.retained) return false
        if (messageId != other.messageId) return false
        if (topic != other.topic) return false
        if (!payload.contentEquals(other.payload)) return false
        if (payloadString != other.payloadString) return false

        return true
    }

    override fun hashCode(): Int {
        var result = qos
        result = 31 * result + retained.hashCode()
        result = 31 * result + messageId
        result = 31 * result + topic.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + payloadString.hashCode()
        return result
    }
}
