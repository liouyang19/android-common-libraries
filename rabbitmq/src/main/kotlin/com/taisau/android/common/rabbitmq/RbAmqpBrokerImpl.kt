package com.taisau.android.common.rabbitmq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

class RbAmqpBrokerImpl(
    private val config: RbAmqpConfig
) : RbAmqpBroker{
    override fun establishConnection(): Flow<RbAmqpConnection> = callbackFlow {
        val connection = if (config.autoReconnect){
            AutoReconnectAmqpConnection(config).also { it.start() }
        }else{
           val raw = RawAmqpConnection(config)
            raw.connect()
            AutoReconnectAmqpConnection(config)
        }
        send(connection)
        awaitClose {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}