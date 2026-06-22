package com.taisau.android.common.rabbitmq

/**
 * 交换机类型
 *
 * RabbitMQ 交换机类型枚举，使用密封类实现类型安全。
 *
 * - [Direct]：直接交换，根据路由键精确匹配
 * - [Fanout]：扇出交换，广播到所有绑定的队列
 * - [Topic]：主题交换，根据路由键模式匹配
 * - [Headers]：头交换，根据消息头属性匹配
 */
sealed class ExchangeType {
    /** 直接交换：路由键精确匹配 */
    object Direct : ExchangeType()

    /** 扇出交换：广播到所有绑定的队列 */
    object Fanout : ExchangeType()

    /** 主题交换：路由键模式匹配（支持 * 和 #） */
    object Topic : ExchangeType()

    /** 头交换：根据消息头属性匹配 */
    object Headers : ExchangeType()

    internal fun toAmqpType(): String = when (this) {
        Direct -> "direct"
        Fanout -> "fanout"
        Topic -> "topic"
        Headers -> "headers"
    }
}
