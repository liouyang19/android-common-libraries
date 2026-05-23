package com.taisau.android.common.rabbitmq

import kotlinx.coroutines.flow.Flow

/**
 * RbAmqpBroker  - RabbitMQ 节点
 */
interface RbAmqpBroker {

    /**
     * 建立连接，返回Flow<RbAmqpConnection>
     * Flow 订阅者会自动建立连接,取消时断开连接
     */
    fun establishConnection(): Flow<RbAmqpConnection>
}