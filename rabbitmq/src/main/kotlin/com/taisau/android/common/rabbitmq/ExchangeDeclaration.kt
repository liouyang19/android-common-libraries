package com.taisau.android.common.rabbitmq

/**
 * 交换机声明配置
 *
 * 定义交换机的属性。
 *
 * @property name 交换机名称
 * @property type 交换机类型
 * @property durable 是否持久化，默认 true
 * @property autoDelete 是否自动删除，默认 false
 *
 * @see ExchangeType
 */
data class ExchangeDeclaration(
    val name: String,
    val type: ExchangeType,
    val durable: Boolean = true,
    val autoDelete: Boolean = false
)
