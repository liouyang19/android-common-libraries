package com.taisau.android.common.rabbitmq

/**
 * RabbitMQ 客户端入口接口
 *
 * 提供创建 [RbAmqpBroker] 的工厂方法，是使用 Flow 响应式 API 的入口点。
 *
 * 使用示例：
 * ```
 * val client = RbAmqpClient.create()
 * val broker = client.getBroker(config)
 * broker.establishConnection().collect { connection ->
 *     connection.consume("my_queue").collect { msg ->
 *         println("收到消息: ${String(msg.body)}")
 *     }
 * }
 * ```
 */
interface RbAmqpClient {

    /**
     * 根据配置创建 [RbAmqpBroker] 实例
     *
     * @param config RabbitMQ 连接配置
     * @return [RbAmqpBroker] 实例
     */
    fun getBroker(config: RbAmqpConfig): RbAmqpBroker

    companion object {
        /**
         * 创建默认的 RbAmqpClient 实现
         */
        fun create(): RbAmqpClient = RbAmqpClientImpl()
    }
}
