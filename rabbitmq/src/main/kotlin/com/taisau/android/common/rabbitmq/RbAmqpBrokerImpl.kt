package com.taisau.android.common.rabbitmq

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * RbAmqpBroker 实现类
 *
 * 根据配置选择原始连接或带自动重连的连接。
 *
 * @param config RabbitMQ 配置
 */
class RbAmqpBrokerImpl(
    private val config: RbAmqpConfig
) : RbAmqpBroker {

    /**
     * 建立连接，返回连接流
     *
     * - autoReconnect=true: 返回带自动重连的连接（推荐）
     * - autoReconnect=false: 返回原始连接，断线后需要手动重连
     */
    override fun establishConnection(): Flow<RbAmqpConnection> = callbackFlow {
        val closeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val connection: RbAmqpConnection = if (config.autoReconnect) {
            AutoReconnectAmqpConnection(config).also { it.start() }
        } else {
            val raw = RawAmqpConnection(config)
            raw.connect()
            raw
        }
        send(connection)
        awaitClose {
            closeScope.launch {
                connection.disconnect()
            }
        }
    }.flowOn(Dispatchers.IO)
}
