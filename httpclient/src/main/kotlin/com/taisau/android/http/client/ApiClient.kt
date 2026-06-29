package com.taisau.android.http.client

import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 模仿 Ktor [HttpClient] 风格的 Retrofit 封装。
 *
 * 核心特性：
 * - **DSL 构造函数**：`ApiClient { ... }` 替代 `RetrofitConfig.Builder`
 * - **插件系统**：`install(Logging) { ... }` 替代手动组合拦截器
 * - **URL 直接切换**：`client.baseUrl = "https://new.url"` 自动重建实例
 *
 * ## 使用示例
 *
 * ```kotlin
 * val client = ApiClient {
 *     baseUrl("https://api.example.com")
 *
 *     install(Logging) { level = HttpLoggingInterceptor.Level.BODY }
 *     install(Auth) { tokenProvider { SessionManager.token } }
 *     install(Retry) { maxRetries = 3 }
 *
 *     connectTimeout = 15_000L
 *     readTimeout = 15_000L
 * }
 *
 * // 创建 API 服务
 * val api = client.create<UserApi>()
 * val users = api.listUsers()
 *
 * // 运行时直接切换 URL
 * client.baseUrl = "https://dev.api.example.com"
 * val devApi = client.create<UserApi>() // 新 URL ✅
 * ```
 *
 * @see ApiClientConfig
 * @see ApiPlugin
 */
class ApiClient internal constructor(
    internal val config: ApiClientConfig,
) : AutoCloseable {

    /**
     * 当前 baseUrl。
     *
     * 设置新值后自动清空缓存，下次 [create] 时使用新 URL 创建 Retrofit 实例。
     *
     * ```kotlin
     * client.baseUrl = "https://new.api.example.com"
     * val newApi = client.create<MyApi>()
     * ```
     */
    var baseUrl: String
        get() = config.baseUrl
        set(value) {
            if (config.baseUrl == value) return
            config.baseUrl = value
            cached = null
        }

    /** 缓存的 Retrofit + OkHttpClient 实例 */
    @Volatile
    private var cached: CachedResources? = null

    private val lock = Any()

    /**
     * 创建 API 服务接口实例。
     *
     * @param serviceClass API 接口的 Class
     * @return Retrofit 动态代理实例
     */
    fun <T : Any> create(serviceClass: Class<T>): T {
        return getOrBuild().create(serviceClass)
    }

    /**
     * 创建 API 服务接口实例（reified 版本）。
     *
     * ```kotlin
     * val api = client.create<UserApi>()
     * ```
     */
    inline fun <reified T : Any> create(): T {
        return create(T::class.java)
    }

    /**
     * 释放资源。
     *
     * - 清空 Retrofit 缓存
     * - Shutdown OkHttp 连接池（关闭空闲连接）
     * - Shutdown OkHttp Dispatcher（取消排队请求）
     *
     * 调用后 [create] 会自动重建新实例。
     */
    override fun close() {
        synchronized(lock) {
            cached?.let { res ->
                res.okHttpClient.dispatcher.executorService.shutdown()
                res.okHttpClient.connectionPool.evictAll()
            }
            cached = null
        }
    }

    /** 缓存资源：Retrofit + OkHttpClient 成对保存 */
    private data class CachedResources(
        val retrofit: Retrofit,
        val okHttpClient: OkHttpClient,
    )

    /**
     * 获取或构建 Retrofit 实例。
     * 线程安全：首次创建时加锁，后续无锁读取。
     */
    private fun getOrBuild(): Retrofit {
        cached?.let { return it.retrofit }
        synchronized(lock) {
            cached?.let { return it.retrofit }
            val resources = buildResources()
            cached = resources
            return resources.retrofit
        }
    }

    /** 构建 Retrofit + OkHttpClient 资源对 */
    private fun buildResources(): CachedResources {
        val okHttp = buildOkHttpClient()
        val retrofit = Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(okHttp)
            .apply {
                val converterFactories = config.converterFactories.toList()
                if (converterFactories.isEmpty()) {
                    addConverterFactory(createJsonConverterFactory())
                } else {
                    converterFactories.forEach { addConverterFactory(it) }
                }
                config.callAdapterFactories.forEach { addCallAdapterFactory(it) }
            }
            .build()
        return CachedResources(retrofit, okHttp)
    }

    /** 构建 OkHttpClient */
    private fun buildOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {
            connectTimeout(config.connectTimeout, TimeUnit.MILLISECONDS)
            readTimeout(config.readTimeout, TimeUnit.MILLISECONDS)
            writeTimeout(config.writeTimeout, TimeUnit.MILLISECONDS)

            // 按优先级排序后添加（正序 = 优先级低的先执行）
            config.interceptors
                .sortedBy { it.priority }
                .forEach { addInterceptor(it.interceptor) }

            config.tokenProvider?.let { provider ->
                addInterceptor { chain ->
                    val token = provider()
                    val request = if (token != null) {
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    } else {
                        chain.request()
                    }
                    chain.proceed(request)
                }
            }

            config.networkInterceptors.forEach { addNetworkInterceptor(it) }

            val dispatcher = Dispatcher()
            dispatcher.maxRequests = config.maxParallelRequests
            dispatcher.maxRequestsPerHost = config.maxRequestsPerHost
            this.dispatcher(dispatcher)
            // 应用 okHttp {} 定制器（缓存、CookieJar 等）
            config.okHttpBuilderModifier?.let { apply(it) }
        }.build()
    }

    /** 创建默认 kotlinx.serialization JSON 转换器 */
    private fun createJsonConverterFactory(): retrofit2.Converter.Factory {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        }
        val contentType = "application/json".toMediaType()
        return json.asConverterFactory(contentType)
    }

    companion object {
        /**
         * 创建 [ApiClient] 实例。
         *
         * ```kotlin
         * val client = ApiClient {
         *     baseUrl("https://api.example.com")
         *     install(Logging) { level = ... }
         * }
         * ```
         */
        operator fun invoke(config: ApiClientConfig.() -> Unit): ApiClient {
            val clientConfig = ApiClientConfig().apply(config)
            clientConfig.validate()
            return ApiClient(clientConfig)
        }
    }
}
