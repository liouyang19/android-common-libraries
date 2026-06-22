package com.taisau.android.common.rabbitmq

/**
 * [RbAmqpClient] 的默认实现
 *
 * 根据配置创建 [RbAmqpBrokerImpl] 实例。
 */
class RbAmqpClientImpl : RbAmqpClient {

    override fun getBroker(config: RbAmqpConfig): RbAmqpBroker {
        return RbAmqpBrokerImpl(config)
    }
}
