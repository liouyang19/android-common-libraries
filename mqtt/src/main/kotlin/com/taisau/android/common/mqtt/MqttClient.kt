package com.taisau.android.common.mqtt

import android.content.Context

interface MqttClient {

    fun getBroker(serverUri:  String,config:MqttConfig): MqttBroker


    companion object{

        fun create(context: Context,baseConfig) = MqttClientImpl(context,baseConfig)
    }
}