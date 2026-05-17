package com.taisau.android.common.rabbitmq

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// RabbitMQ.kt - 主入口点
object RabbitMQ {

    // 创建客户端
    fun client(block: RabbitMQClient.Builder.() -> Unit): RabbitMQClient.Builder {
        return RabbitMQClient.create(block)
    }

    // 消息路由
    fun route(client: RabbitMQClient, block: RabbitMQRoute.() -> Unit): RabbitMQRoute {
        return RabbitMQRoute(client).apply(block)
    }

    // 消息发布
    fun publish(client: RabbitMQClient, block: RabbitMQPublisher.() -> Unit): RabbitMQPublisher {
        return RabbitMQPublisher(client).apply(block)
    }
}

// 应用级别扩展
fun Application.rabbitmq(block: suspend RabbitMQClient.() -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        // 这里可以集成到 Android Application 中
    }
}