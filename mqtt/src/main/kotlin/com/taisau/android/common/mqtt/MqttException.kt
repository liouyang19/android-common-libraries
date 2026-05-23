package com.taisau.android.common.mqtt

class MqttException(
    message: String,
    cause: Throwable? = null
): Exception(message,cause)