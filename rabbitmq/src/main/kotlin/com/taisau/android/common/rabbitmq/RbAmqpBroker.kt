package com.taisau.android.common.rabbitmq

import kotlinx.coroutines.flow.Flow

interface RbAmqpBroker {
	/**
	 * 建立连接，返回 Flow<RbAmqpConnection>。
	 * Flow 被收集时连接，取消时断开。
	 */
	fun establishConnection(): Flow<RbAmqpConnection>
}