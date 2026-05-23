package com.taisau.android.common.mqtt

import kotlinx.coroutines.flow.Flow

interface MqttBroker {

    fun establishConnection(): Flow<MqttConnection>
}