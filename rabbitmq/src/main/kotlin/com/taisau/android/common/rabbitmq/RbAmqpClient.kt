package com.taisau.android.common.rabbitmq

interface  RbAmqpClient {
	
	fun getBroker(config: RbAmqpConfig): RbAmqpBroker
	
	
	companion object {
		fun create(): RbAmqpClient = RbAmqpClientImpl()
	}
}