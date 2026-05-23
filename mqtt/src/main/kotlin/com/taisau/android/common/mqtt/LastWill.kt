package com.taisau.android.common.mqtt

data class LastWill(
    val topic: String,
    val payload: String,
    val qos: Int = 1,
    val retained: Boolean = false
)