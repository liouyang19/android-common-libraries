package com.taisau.android.common.rabbitmq

import kotlinx.coroutines.flow.Flow

/**
 * RabbitMQ 连接代理接口
 *
 * 负责根据配置建立与 RabbitMQ 服务器的连接。
 * 使用 Flow 模式管理连接生命周期：订阅时建立连接，取消时断开连接。
 *
 * @see RbAmqpBrokerImpl
 */
interface RbAmqpBroker {

    /**
     * 建立连接，返回 [RbAmqpConnection] 流
     *
     * Flow 订阅者会自动触发连接建立，取消 Flow 时会自动断开连接。
     *
     * @return [RbAmqpConnection] 流，发射一次连接实例
     */
    fun establishConnection(): Flow<RbAmqpConnection>
}
