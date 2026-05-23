package com.taisau.android.common.mqtt

import kotlinx.coroutines.flow.Flow

interface MqttConnection {

    fun publish(
        topic: String,
        payload: ByteArray,
        qos: Int =1,
        retained: Boolean = false
    ):  Flow<Int>

    fun subscribe(
        topic: String,
        qos: Int = 1
    ): Flow<ByteArray>

    suspend fun unsubscribe(topic: String)

    suspend fun disconnect()

    val isConnected: Boolean
}