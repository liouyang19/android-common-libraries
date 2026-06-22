package com.taisau.android.common.mqtt

import kotlinx.coroutines.flow.Flow

class PahoMqttConnection: MqttConnection {
    override fun publish(
        topic: String,
        payload: ByteArray,
        qos: Int,
        retained: Boolean
    ): Flow<Int> {
        TODO("Not yet implemented")
    }

    override fun subscribe(
        topic: String,
        qos: Int
    ): Flow<ByteArray> {
        TODO("Not yet implemented")
    }

    override suspend fun unsubscribe(topic: String) {
        TODO("Not yet implemented")
    }

    override suspend fun disconnect() {
        TODO("Not yet implemented")
    }

    override val isConnected: Boolean
        get() = TODO("Not yet implemented")
}