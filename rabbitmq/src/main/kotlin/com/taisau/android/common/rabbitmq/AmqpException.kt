package com.taisau.android.common.rabbitmq

/**
 * RabbitMQ 操作自定义异常
 *
 * @param message 异常描述信息
 * @param cause 原始异常原因（可选）
 */
class AmqpException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
