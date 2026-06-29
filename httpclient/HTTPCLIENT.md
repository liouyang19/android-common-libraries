# HttpClient — Ktor 风格 Android HTTP 客户端

模块 `:httpclient` · 包 `com.taisau.android.http.client`  
基于 **OkHttp 5 + Retrofit 2 + kotlinx.serialization**

---

## 目录

1. [快速开始](#快速开始)
2. [ApiClient — 核心入口](#apiclient--核心入口)
3. [插件系统](#插件系统)
4. [内置插件清单](#内置插件清单)
5. [SSE 支持](#sse-支持)
6. [WebSocket 支持](#websocket-支持)
7. [进度监听 + 下载限速](#进度监听--下载限速)
8. [ApiResult 统一返回](#apiresult-统一返回)
9. [CallAdapter / Converter 扩展](#calladapter--converter-扩展)
10. [扩展点：自定义插件](#扩展点自定义插件)
11. [OkHttp 直接定制](#okhttp-直接定制)
12. [package.json 包结构总览](#包结构总览)

---

## 快速开始

```kotlin
// 1. 创建客户端
val client = ApiClient {
    baseUrl("https://api.example.com")

    // 插件
    install(Logging) { level = HttpLoggingInterceptor.Level.BODY }
    install(Auth) { tokenProvider { SessionManager.token } }
    install(Retry) { maxRetries = 3; backoffMultiplier = 2.0 }

    // 超时
    connectTimeout = 15_000L
    readTimeout = 15_000L
}

// 2. 定义 API 接口
interface UserApi {
    @GET("users")
    suspend fun listUsers(): List<User>

    @POST("users")
    suspend fun createUser(@Body user: User): User
}

// 3. 调用
val api = client.create<UserApi>()
val users = api.listUsers()

// 4. 运行时切换 URL
client.baseUrl = "https://dev.api.example.com"

// 5. 资源释放
client.close()
```

---

## ApiClient — 核心入口

### 构造函数

```kotlin
val client = ApiClient {
    // ApiClientConfig.() -> Unit  DSL
}
```

### 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `baseUrl` | `String` | 当前 baseUrl，设置新值自动重建 Retrofit |

### 方法

| 方法 | 说明 |
|------|------|
| `create<T>()` | 创建 Retrofit API 服务 |
| `create(serviceClass)` | 同上，Class 版本 |
| `close()` | 释放资源（关闭 OkHttp 连接池 + 线程池） |

### ApiClientConfig DSL

| DSL 方法 | 说明 |
|----------|------|
| `baseUrl(url)` | 设置 baseUrl |
| `connectTimeout / readTimeout / writeTimeout` | 超时（毫秒） |
| `maxParallelRequests / maxRequestsPerHost` | 并发控制 |
| `install(plugin) { config }` | 安装插件 |
| `interceptor(priority) { chain -> }` | 添加自定义拦截器（可指定优先级） |
| `networkInterceptor(interceptor)` | 添加网络拦截器 |
| `tokenProvider { }` | 设置 Token 提供者 |
| `addConverterFactory(factory)` | 添加 Converter.Factory |
| `addCallAdapterFactory(factory)` | 添加 CallAdapter.Factory |
| `okHttp { builder -> }` | 直接定制 OkHttpClient.Builder |

---

## 插件系统

模仿 Ktor 的插件架构：

```kotlin
interface ApiPlugin<T : Any> {
    val key: ApiPluginKey<T>
    fun createConfig(): T
    fun ApiClientConfig.prepare(config: T)  // 向配置注入拦截器/转换器
}
```

安装方式：

```kotlin
install(Logging) {
    level = HttpLoggingInterceptor.Level.BODY
}
```

### 拦截器执行顺序

所有插件通过 `PrioritizedInterceptor` 注册，构建时按优先级自动排序：

```
Auth (100) → Encryption (200) → Mock (300) → Retry (400) → Cache (500) → Logging (600)
```

自定义拦截器也可指定优先级：

```kotlin
interceptor(priority = 350) { chain ->
    chain.proceed(chain.request())
}
```

---

## 内置插件清单

| 插件 | 说明 | 默认优先级 |
|------|------|-----------|
| [Logging](#logging) | HTTP 日志 | 600 |
| [Auth](#auth) | Bearer Token 认证 | 100 |
| [Retry](#retry) | 请求重试（指数退避+抖动） | 400 |
| [ContentNegotiation](#contentnegotiation) | JSON 序列化配置 | - |
| [Encryption](#encryption) | 请求加密 / 响应解密 | 200 |
| [CachePlugin](#cacheplugin) | 磁盘缓存 + 强制缓存 | 500 |
| [Cookies](#cookies) | Cookie 管理（内存/持久化） | - |
| [Mock](#mock) | 请求模拟（测试用） | 300 |

---

### Logging

```kotlin
install(Logging) {
    level = HttpLoggingInterceptor.Level.BODY
    logger = HttpLoggingInterceptor.Logger.DEFAULT
    // 或完全自定义
    customInterceptor = Interceptor { chain ->
        Log.d("HTTP", "${chain.request().method} ${chain.request().url}")
        chain.proceed(chain.request())
    }
}
```

### Auth

```kotlin
install(Auth) {
    tokenProvider { SessionManager.token }           // Token 提供者
    authScheme = "Bearer"                            // 认证 scheme
    onTokenExpired = { SessionManager.refresh() }    // 401 自动刷新
    refreshedTokenProvider { SessionManager.token }  // 刷新后的新 Token

    // 或完全自定义认证
    customInterceptor = Interceptor { chain ->
        chain.proceed(chain.request().newBuilder()
            .header("X-API-Key", getApiKey())
            .build())
    }
}
```

### Retry

```kotlin
install(Retry) {
    maxRetries = 3                  // 最大重试次数
    delayMs = 1000L                 // 首次间隔
    backoffMultiplier = 2.0         // 指数退避倍数
    maxDelayMs = 30_000L            // 最长等待上限
    jitter = 0.2                    // 随机抖动 ±20%
    retryOnNetworkError = true      // 网络异常重试
    retryOnHttpError = false        // HTTP 错误重试
    retryIf = { response ->         // 自定义重试条件
        response.code == 429
    }

    // 或完全自定义
    customInterceptor = Interceptor { chain ->
        // 自定义重试逻辑
        chain.proceed(chain.request())
    }
}
```

### ContentNegotiation

```kotlin
install(ContentNegotiation) {
    json(Json { ignoreUnknownKeys = true })
    // 或添加额外 Converter
    addConverter(MyProtoConverterFactory())
}
```

### Encryption

```kotlin
install(Encryption) {
    requestEncryptor = RequestEncryptor { data, contentType, headers ->
        headers["X-Encrypt"] = "AES-256-GCM"
        aesEncrypt(data, secretKey)
    }
    responseDecryptor = ResponseDecryptor { data, headers ->
        aesDecrypt(data, secretKey)
    }
    encryptedContentType = "application/octet-stream"

    // 或完全自定义
    customRequestInterceptor = Interceptor { chain ->
        val encrypted = encrypt(chain.request().body?.bytes() ?: return@Interceptor chain.proceed(chain.request()))
        chain.proceed(chain.request().newBuilder()
            .method(chain.request().method, encrypted.toRequestBody(...))
            .build())
    }
}
```

### CachePlugin

```kotlin
install(CachePlugin) {
    directory = cacheDir                  // 缓存目录（必填）
    maxSize = 10 * 1024 * 1024            // 10MB
    forceCache = true                     // 强制缓存 GET（忽略服务端 Cache-Control）
    maxAgeSeconds = 300                   // 缓存时长
}
```

### Cookies

```kotlin
// 内存模式
install(Cookies) { persistent = false }

// 文件持久化
install(Cookies) {
    storage = PersistentCookieStorage(cacheDir)
}

// 自定义存储
install(Cookies) {
    storage = object : CookieStorage {
        override fun loadAll() = db.getAllCookies()
        override fun save(url, cookies) = db.saveCookies(url, cookies)
        override fun remove(url) = db.removeCookies(url)
        override fun clear() = db.clearCookies()
    }
}
```

### Mock

```kotlin
install(Mock) {
    // 精确匹配
    mockGet("/users") {
        code = 200
        body = """[{"id":1,"name":"Alice"}]"""
    }
    // 前缀匹配（* 结尾）
    mockPost("/users/") {
        code = 201
        body = """{"id":2,"name":"Bob"}"""
    }
    mockDelete("/users/*") { code = 204 }

    // 模拟延迟 + 随机失败
    delayMs = 500L          // 所有 mock 响应延迟 500ms
    failureRate = 0.3       // 30% 概率抛 IOException
}
```

---

## SSE 支持

基于 `okhttp-sse` 库，返回 Kotlin `Flow`。

```kotlin
// 冷流 — collect 时连接，取消时断开
client.sse("/events").collect { event ->
    when (event) {
        is SseEvent.Connecting -> println("连接中…")
        is SseEvent.Connected  -> println("已连接")
        is SseEvent.Data       -> println("[${event.eventType}] ${event.data}")
        is SseEvent.Error      -> println("错误: ${event.throwable}")
        is SseEvent.Disconnected -> println("已断开")
    }
}

// 带配置
client.sse("/stream") {
    autoReconnect = true
    retryDelay = 3000L
    lastEventId = "evt-100"
    headers["Authorization"] = "Bearer $token"
}
```

| 特性 | 说明 |
|------|------|
| 协议解析 | OkHttp 官方 `EventSources` 处理 |
| 重连 | 自动重连，携带 Last-Event-ID |
| 超时 | 读取超时 = 0（长连接） |
| 类型 | 冷 Flow |

---

## WebSocket 支持

基于 OkHttp 原生 WebSocket，返回**热流** `SharedFlow`。

```kotlin
// 创建连接
val ws = client.webSocket("/ws/chat") {
    autoReconnect = true
    reconnectDelay = 3000L
    headers["Authorization"] = "Bearer $token"
}

// 接收消息（热流，创建即连接）
ws.messages.collect { event ->
    when (event) {
        is WsEvent.Connecting -> showStatus("连接中…")
        is WsEvent.Connected  -> showStatus("已连接")
        is WsEvent.Text      -> showMessage(event.text)
        is WsEvent.Binary    -> showBinary(event.bytes)
        is WsEvent.Error     -> logError(event.throwable)
        is WsEvent.Disconnected -> showStatus("已断开")
    }
}

// 发送
ws.send("Hello!")
ws.send(byteArrayOf(0x01, 0x02))

// 关闭
ws.close()
```

| 特性 | 说明 |
|------|------|
| 热流 | SharedFlow，多 collector 共享 |
| URL 转换 | `https://` → `wss://`，`http://` → `ws://` |
| 重连 | 自动重连 + 可配置延迟 |
| 超时 | 读写超时 = 0 |

---

## 进度监听 + 下载限速

### 上传进度

```kotlin
val fileBody = File("photo.jpg").asRequestBody("image/jpeg".toMediaTypeOrNull())
val progressBody = ProgressRequestBody(fileBody) { written, total, speed ->
    updateProgress(written, total, speed)
}
```

### 下载进度 + 限速

```kotlin
lifecycleScope.launch {
    val result = client.download("/files/bigfile.zip") {
        destFile = File(cacheDir, "update.zip")
        speedLimit = 500 * 1024  // 限速 500 KB/s
        onProgress = { downloaded, total, speed ->
            binding.progressBar.progress = (downloaded * 100 / total).toInt()
            binding.speedText.text = formatSpeed(speed)
        }
    }

    when (result) {
        is DownloadResult.Success -> installApk(result.file)
        is DownloadResult.Error -> toast("失败: ${result.throwable.message}")
    }
}
```

---

## ApiResult 统一返回

```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
    data class Exception(val throwable: Throwable) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
}
```

### 自动封装（CallAdapter 方式）

```kotlin
val client = ApiClient {
    addCallAdapterFactory(ApiResultCallAdapterFactory())
}

interface UserApi {
    @GET("users")
    suspend fun listUsers(): ApiResult<List<User>>  // 自动包装
}

val api = client.create<UserApi>()
when (val result = api.listUsers()) {
    is ApiResult.Success -> handle(result.data)
    is ApiResult.Error -> showError("${result.code}: ${result.message}")
    is ApiResult.Exception -> logError(result.throwable)
    is ApiResult.Loading -> {}
}
```

| HTTP 结果 | 包装类型 |
|-----------|---------|
| 200 + body | `Success(data)` |
| 200 + null body | `Error(200, "body is null")` |
| 4xx / 5xx | `Error(code, message)` |
| 网络异常 | `Exception(IOException)` |
| 解析异常 | `Exception(Throwable)` |

---

## CallAdapter / Converter 扩展

```kotlin
val client = ApiClient {
    // Converter.Factory
    addConverterFactory(GsonConverterFactory.create())
    addConverterFactory(MyProtoConverterFactory())

    // CallAdapter.Factory（按注册顺序匹配返回类型）
    addCallAdapterFactory(ApiResultCallAdapterFactory())
    addCallAdapterFactory(RxJava3CallAdapterFactory.create())
}
```

---

## 扩展点：自定义插件

```kotlin
data class CacheConfig(val maxAgeSeconds: Int = 60)

object CachePlugin : ApiPlugin<CacheConfig> {
    override val key = ApiPluginKey<CacheConfig>("CachePlugin")
    override fun createConfig() = CacheConfig()
    override fun ApiClientConfig.prepare(config: CacheConfig) {
        interceptors += PrioritizedInterceptor(
            interceptor = Interceptor { chain ->
                chain.proceed(chain.request()).newBuilder()
                    .header("Cache-Control", "public, max-age=${config.maxAgeSeconds}")
                    .build()
            },
            priority = InterceptorOrder.CUSTOM,  // 或自定义优先级
        )
    }
}

// 使用
val client = ApiClient {
    install(CachePlugin) { maxAgeSeconds = 120 }
}
```

---

## OkHttp 直接定制

用于设置拦截器无法实现的功能：

```kotlin
val client = ApiClient {
    okHttp {
        cache(Cache(cacheDir, 10 * 1024 * 1024))
        cookieJar(MyCookieJar())
        proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("proxy", 8080)))
        addInterceptor(MyInterceptor())
    }
}
```

---

## 包结构总览

```
httpclient/src/main/kotlin/com/taisau/android/http/client/
├── ApiClient.kt                   ★ Ktor 风格主入口
├── ApiClientConfig.kt             ★ DSL 配置 + okHttp 定制器
├── ApiPlugin.kt                   ★ 插件接口 + 插件键
├── ApiResult.kt                   ★ 统一返回类型
├── ApiResultCallAdapterFactory.kt ★ ApiResult 自动封装 CallAdapter
├── ApiCaller.kt                   ★ 手动请求辅助工具（deprecated）
├── ApiInterceptor.kt              ★ 拦截器工厂（deprecated）
│
├── plugins/
│   ├── Auth.kt                    Bearer Token 认证
│   ├── Logging.kt                 HTTP 日志
│   ├── Retry.kt                   重试（指数退避+抖动）
│   ├── ContentNegotiation.kt      JSON 序列化配置
│   ├── Encryption.kt              请求加密 / 响应解密
│   ├── Cache.kt                   磁盘缓存
│   ├── Cookies.kt                 Cookie 管理
│   └── Mock.kt                    请求模拟
│
├── sse/
│   ├── SseClient.kt               SSE 支持（okhttp-sse）
│   └── SseEvent.kt                SSE 事件类型
│
├── ws/
│   ├── WsClient.kt                WebSocket 支持（OkHttp）
│   └── WsEvent.kt                 WebSocket 事件类型
│
├── progress/
│   ├── ProgressBody.kt            上传/下载进度包装 + 速度限制
│   └── Download.kt                文件下载扩展
│
└── 废弃（保留兼容）/
    ├── RetrofitClient.kt           → 请用 ApiClient
    ├── RetrofitConfig.kt           → 请用 ApiClientConfig
    └── EnvironmentAwareApi.kt      → ApiClient 已内置
```

---

## 依赖

```kotlin
dependencies {
    api("com.squareup.retrofit2:retrofit:3.0.0")
    api("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:okhttp-sse:5.3.2")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")
}
```
