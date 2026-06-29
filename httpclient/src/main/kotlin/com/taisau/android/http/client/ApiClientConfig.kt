package com.taisau.android.http.client

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.CallAdapter
import retrofit2.Converter
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** 带优先级的拦截器。构建 OkHttpClient 时按 [priority] 升序排列。 */
internal class PrioritizedInterceptor(
    val interceptor: Interceptor,
    val priority: Int,
)

/** 内置插件默认优先级 */
object InterceptorOrder {
    const val AUTH = 100
    const val ENCRYPTION = 200
    const val MOCK = 300
    const val RETRY = 400
    const val CACHE = 500
    const val LOGGING = 600
    const val CUSTOM = 500
}

/**
 * ApiClient 配置类，模仿 Ktor 的 [HttpClientConfig]。
 *
 * 通过 [ApiClient] 的 DSL 构造器配置：
 *
 * ```kotlin
 * val client = ApiClient {
 *     baseUrl("https://api.example.com")
 *
 *     // ── 插件安装 ──
 *     install(Logging) { level = HttpLoggingInterceptor.Level.BODY }
 *     install(Auth) { tokenProvider { SessionManager.token } }
 *
 *     // ── 超时 ──
 *     connectTimeout = 15_000L
 *     readTimeout = 15_000L
 * }
 * ```
 *
 * 运行时切换 URL：
 * ```kotlin
 * client.baseUrl = "https://dev.api.example.com"
 * client.create<Api>()  // 使用新 URL
 * ```
 */
open class ApiClientConfig {

    // ═══════════════════════════════════════
    // 基础 URL
    // ═══════════════════════════════════════

    /**
     * 当前 baseUrl。
     *
     * 通过 [ApiClient.baseUrl] 切换时自动清空 Retrofit 缓存，下次 [ApiClient.create] 使用新 URL。
     */
    var baseUrl: String = ""
        internal set

    /** 设置 baseUrl */
    fun baseUrl(url: String) {
        baseUrl = url
    }

    // ═══════════════════════════════════════
    // 超时配置（单位：毫秒）
    // ═══════════════════════════════════════

    /** 连接超时（毫秒），默认 30 秒 */
    var connectTimeout: Long = TimeUnit.SECONDS.toMillis(30)

    /** 读取超时（毫秒），默认 30 秒 */
    var readTimeout: Long = TimeUnit.SECONDS.toMillis(30)

    /** 写入超时（毫秒），默认 30 秒 */
    var writeTimeout: Long = TimeUnit.SECONDS.toMillis(30)

    // ═══════════════════════════════════════
    // 并发配置
    // ═══════════════════════════════════════

    /** 最大并行请求数 */
    var maxParallelRequests: Int = 64

    /** 每台主机最大请求数 */
    var maxRequestsPerHost: Int = 5

    // ═══════════════════════════════════════
    // 拦截器（带优先级排序）
    // ═══════════════════════════════════════

    /**
     * 应用拦截器列表（带优先级）。
     *
     * 内置插件默认优先级：
     * - Auth (100) → Encryption (200) → Mock (300) → Retry (400) → Cache (500) → Logging (600)
     *
     * 构建 [ApiClient] 时按优先级排序，无需手动管理添加顺序。
     */
    internal val interceptors: MutableList<PrioritizedInterceptor> = mutableListOf()

    /**
     * 网络拦截器列表（不带优先级，按添加顺序执行）。
     */
    internal val networkInterceptors: MutableList<Interceptor> = mutableListOf()

    /**
     * [Converter.Factory] 列表，按添加顺序依次尝试。
     *
     * 可通过以下方式添加：
     * - [install] `ContentNegotiation` 插件添加 JSON 转换器
     * - 直接调用 [addConverterFactory] 方法
     *
     * 如果列表为空，默认使用 `kotlinx.serialization` JSON 作为兜底。
     */
    val converterFactories: MutableList<Converter.Factory> = mutableListOf()

    /**
     * [CallAdapter.Factory] 列表。
     *
     * 最常用的是 [ApiResultCallAdapterFactory]，将返回值自动封装为 [ApiResult]。
     *
     * 也可添加其他适配器（如 RxJava），但注意多个 CallAdapter 会按顺序尝试，
     * 第一个匹配返回类型的会被使用。
     */
    val callAdapterFactories: MutableList<CallAdapter.Factory> = mutableListOf()

    /**
     * Token 提供者（为不使用 [Auth] 插件的场景提供便捷配置）。
     * 如果已安装 [Auth] 插件，此处可留空。
     */
    internal var tokenProvider: (() -> String?)? = null

    /**
     * OkHttpClient.Builder 定制器（供插件/用户修改构建器）。
     *
     * 例如设置缓存、CookieJar 等无法通过拦截器实现的功能：
     * ```kotlin
     * okHttp { cache(Cache(cacheDir, 10L * 1024 * 1024)) }
     * ```
     */
    internal var okHttpBuilderModifier: (OkHttpClient.Builder.() -> Unit)? = null

    // ═══════════════════════════════════════
    // 公共 DSL 方法
    // ═══════════════════════════════════════

    /**
     * 安装插件。
     *
     * ```kotlin
     * install(Logging) {
     *     level = HttpLoggingInterceptor.Level.BODY
     * }
     * ```
     */
    fun <T : Any> install(plugin: ApiPlugin<T>, configure: T.() -> Unit = {}) {
        val config = plugin.createConfig().apply(configure)
        plugin.apply {
            this@ApiClientConfig.prepare(config)
        }
    }

    /** 添加自定义应用拦截器（lambda 方式，默认优先级 500） */
    fun interceptor(
        priority: Int = InterceptorOrder.CUSTOM,
        block: okhttp3.Interceptor.Chain.(okhttp3.Request) -> okhttp3.Response,
    ) {
        interceptors += PrioritizedInterceptor(
            interceptor = okhttp3.Interceptor { chain ->
                block(chain, chain.request())
            },
            priority = priority,
        )
    }

    /** 添加自定义 [Interceptor]（可指定优先级） */
    fun interceptor(
        interceptor: Interceptor,
        priority: Int = InterceptorOrder.CUSTOM,
    ) {
        interceptors += PrioritizedInterceptor(
            interceptor = interceptor,
            priority = priority,
        )
    }

    /** 添加网络拦截器 */
    fun networkInterceptor(interceptor: Interceptor) {
        networkInterceptors += interceptor
    }

    /**
     * 设置 Token 提供者（简单场景，复杂场景请使用 [Auth] 插件）。
     */
    fun tokenProvider(provider: () -> String?) {
        tokenProvider = provider
    }

    /**
     * 添加自定义 [retrofit2.Converter.Factory]。
     *
     * 支持同时使用多个 Converter，按添加顺序依次尝试。
     *
     * ```kotlin
     * val client = ApiClient {
     *     baseUrl("https://api.example.com")
     *     addConverterFactory(GsonConverterFactory.create())
     *     addConverterFactory(MyCustomConverterFactory())
     * }
     * ```
     */
    fun addConverterFactory(factory: Converter.Factory) {
        converterFactories += factory
    }

    /**
     * 添加自定义 [CallAdapter.Factory]。
     *
     * 常用于将返回值自动封装为 [ApiResult]：
     *
     * ```kotlin
     * val client = ApiClient {
     *     baseUrl("https://api.example.com")
     *     addCallAdapterFactory(ApiResultCallAdapterFactory())
     * }
     * ```
     */
    fun addCallAdapterFactory(factory: CallAdapter.Factory) {
        callAdapterFactories += factory
    }

    /**
     * 直接定制 [OkHttpClient.Builder]。
     *
     * 用于设置拦截器无法实现的功能，如缓存、CookieJar、代理等。
     *
     * ```kotlin
     * val client = ApiClient {
     *     okHttp {
     *         cache(Cache(cacheDir, 10 * 1024 * 1024))
     *         cookieJar(MyCookieJar())
     *         proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("proxy", 8080)))
     *     }
     * }
     * ```
     */
    fun okHttp(block: OkHttpClient.Builder.() -> Unit) {
        okHttpBuilderModifier = block
    }

    /** 构建前校验配置有效性 */
    internal fun validate() {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(connectTimeout > 0) { "connectTimeout must be > 0" }
        require(readTimeout > 0) { "readTimeout must be > 0" }
        require(writeTimeout > 0) { "writeTimeout must be > 0" }
        require(maxParallelRequests > 0) { "maxParallelRequests must be > 0" }
        require(maxRequestsPerHost > 0) { "maxRequestsPerHost must be > 0" }
    }
}
