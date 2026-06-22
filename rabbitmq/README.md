# RabbitMQ 客户端库

基于 [RabbitMQ Java Client](https://github.com/rabbitmq/rabbitmq-java-client) (5.18.0) 的 Android Kotlin 封装库，提供两套 API：

| API | 特点 | 适用场景 |
|-----|------|---------|
| **经典 DSL API** (`RabbitMQClient`) | Builder/DSL 模式，Result 返回 | 简单发布/消费，快速集成 |
| **响应式 Flow API** (`RbAmqpClient`) | Kotlin Flow 驱动，自动重连 | 复杂场景，需要流式处理和自动恢复 |

---

## 目录

- [添加依赖](#添加依赖)
- [经典 DSL API](#经典-dsl-api)
- [响应式 Flow API](#响应式-flow-api)
- [配置说明](#配置说明)
- [API 对比](#api-对比)
- [ProGuard 规则](#proguard-规则)

---

## 添加依赖

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { setUrl("https://www.jitpack.io") }
    }
}

// build.gradle.kts (module)
dependencies {
    implementation("com.github.liouyang19.android-common-libraries:rabbitmq:0.1.0")
}
```

---

## 经典 DSL API

适用于快速集成，使用熟悉的 Builder/DSL 风格。

### 1. 创建客户端

```kotlin
val client = RabbitMQClient.create {
    host("192.168.1.100")
    port(5672)
    credentials("admin", "password")
    virtualHost("/my_vhost")
    timeouts(connection = 5000, handshake = 10000)
    automaticRecovery(enabled = true, interval = 3000)
}
// 注意：build() 会自动连接服务器
```

### 2. 发布消息

```kotlin
// 发布到队列
RabbitMQPublisher(client)
    .toQueue("task_queue")
    .persistent()
    .withConfirm()       // 启用发布者确认
    .publish("Hello World")

// 发布到交换机
RabbitMQPublisher(client)
    .toExchange("logs", routingKey = "info")
    .publish("Log message")

// 发布二进制数据
RabbitMQPublisher(client)
    .toQueue("images")
    .publish(imageBytes, contentType = "image/png")
```

### 3. 消费消息

```kotlin
RabbitMQRoute(client)
    .exchange("logs", ExchangeType.Fanout)
    .queue("my_queue")
    .bindTo("logs")
    .prefetch(10)
    .autoAcknowledge()
    .onMessage { msg ->
        println("收到: ${msg.bodyAsString()}")
        // msg.bodyAsJson() 可解析 JSON
    }
    .consume()
```

### 4. 手动确认

```kotlin
RabbitMQRoute(client)
    .queue("work_queue")
    .prefetch(1)           // 每次只取 1 条
    .onMessage { msg ->
        try {
            process(msg)
            // 框架会在回调后自动 ack
        } catch (e: Exception) {
            // 需要手动 nack 时，在回调中处理
        }
    }
    .consume()
```

### 5. 配置项

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `host` | `localhost` | 服务器地址 |
| `port` | `5672` | AMQP 端口 |
| `username` | `guest` | 用户名 |
| `password` | `guest` | 密码 |
| `virtualHost` | `/` | 虚拟主机 |
| `connectionTimeout` | `30000` | 连接超时 (ms) |
| `handshakeTimeout` | `10000` | 握手超时 (ms) |
| `automaticRecovery` | `true` | 自动恢复连接 |
| `networkRecoveryInterval` | `5000` | 恢复间隔 (ms) |

---

## 响应式 Flow API

基于 Kotlin Flow，支持自动重连和流式处理。

### 1. 创建客户端

```kotlin
val config = RbAmqpConfig(
    host = "192.168.1.100",
    username = "admin",
    password = "password",
    autoReconnect = true,
    reconnectDelayMills = 2000L,
    enablePublisherConfirms = true
)

val client = RbAmqpClient.create()
val broker = client.getBroker(config)
```

### 2. 建立连接

```kotlin
// establishConnection() 返回 Flow<RbAmqpConnection>
// 订阅时自动连接，取消时自动断开
broker.establishConnection().collect { connection ->
    // connection 已就绪，可以发布/消费
}
```

### 3. 发布消息

```kotlin
broker.establishConnection().collect { connection ->
    connection.publish(
        exchange = "",
        routingKey = "my_queue",
        body = "Hello".toByteArray()
    ).collect { ack ->
        // 收到发布确认
        if (ack.success) {
            println("发布成功, deliveryTag=${ack.deliveryTag}")
        }
    }
}
```

### 4. 消费消息

```kotlin
broker.establishConnection().collect { connection ->
    connection.consume("my_queue", autoAck = false)
        .collect { msg ->
            println("收到消息: ${String(msg.body)}")

            // 手动确认
            connection.ack(msg.envelope.deliveryTag, false)
        }
}
```

### 5. 自动重连

```kotlin
val config = RbAmqpConfig(
    host = "192.168.1.100",
    username = "admin",
    password = "password",
    autoReconnect = true,            // 启用自动重连
    reconnectDelayMills = 2000L,     // 重连间隔
    maxReconnectAttempts = 10        // 最大重试次数
)

// AutoReconnectAmqpConnection 会自动管理连接生命周期
// 消费者会在重连后自动恢复
```

### 6. 配置项

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `host` | `localhost` | 服务器地址 |
| `port` | `5672` | AMQP 端口 |
| `virtualHost` | `/` | 虚拟主机 |
| `username` | (必填) | 用户名 |
| `password` | (必填) | 密码 |
| `enablePublisherConfirms` | `true` | 发布者确认 |
| `autoReconnect` | `true` | 自动重连 |
| `reconnectDelayMills` | `2000` | 重连延迟 (ms) |
| `maxReconnectAttempts` | `Int.MAX_VALUE` | 最大重试次数 |
| `heartbeat` | `60` | 心跳间隔 (s) |

---

## API 对比

| 功能 | 经典 DSL API | 响应式 Flow API |
|------|-------------|----------------|
| 创建客户端 | `RabbitMQClient.create { }` | `RbAmqpClient.create().getBroker(config)` |
| 连接管理 | `build()` 自动连接 | Flow 订阅时自动连接 |
| 发布消息 | `RabbitMQPublisher(client).toQueue().publish()` | `connection.publish()` |
| 消费消息 | `RabbitMQRoute(client).queue().onMessage { }.consume()` | `connection.consume().collect { }` |
| 自动重连 | RabbitMQ 客户端内置 | AutoReconnectAmqpConnection |
| 返回值 | `Result<T>` | `Flow<T>` |
| 消息模型 | `Message` | `RbAmqpMessage` |
| 发布者确认 | `withConfirm()` | `enablePublisherConfirms` 配置 |

---

## ProGuard 规则

```pro
# rabbitmq
-keep class com.rabbitmq.** { *; }
-dontwarn com.rabbitmq.**
-keep class com.taisau.android.common.rabbitmq.** { *; }
```

---

## 异常处理

```kotlin
// 经典 API：使用 Result
val result = RabbitMQPublisher(client)
    .toQueue("queue")
    .publish("data")
result.onFailure { error ->
    // 处理错误
}

// Flow API：使用 try/catch
try {
    connection.publish("", "queue", data.toByteArray())
        .catch { e -> /* 处理错误 */ }
        .collect { ack -> /* 处理确认 */ }
} catch (e: AmqpException) {
    // 自定义异常
}
```

---

## 许可证

Apache 2.0
