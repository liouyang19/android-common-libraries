package com.taisau.android.common.rabbitmq

/**
 * 队列绑定配置
 *
 * 定义队列的属性和与交换机的绑定关系。
 *
 * @property queue 队列名称
 * @property exchange 绑定的交换机名称（null 表示不绑定）
 * @property routingKey 路由键
 * @property durable 是否持久化（重启后保留），默认 true
 * @property exclusive 是否独占（仅当前连接可用），默认 false
 * @property autoDelete 是否自动删除（无消费者时），默认 false
 */
data class QueueBinding(
    val queue: String,
    val exchange: String? = null,
    val routingKey: String = "",
    val durable: Boolean = true,
    val exclusive: Boolean = false,
    val autoDelete: Boolean = false
)
