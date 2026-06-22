package com.taisau.android.common.rabbitmq

class RbAmqpClientImpl: RbAmqpClient {
    override fun getBroker(config: RbAmqpConfig): RbAmqpBroker {
        return RbAmqpBrokerImpl( config)
    }
}