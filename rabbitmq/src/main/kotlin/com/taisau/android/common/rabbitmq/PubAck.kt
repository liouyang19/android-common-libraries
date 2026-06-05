package com.taisau.android.common.rabbitmq

data class PubAck(
	val deliveryTag: Long,         // 发布时的 sequence number
	val multiple: Boolean,         // 是否批量确认
	val success: Boolean,
	val error: Throwable? = null
)