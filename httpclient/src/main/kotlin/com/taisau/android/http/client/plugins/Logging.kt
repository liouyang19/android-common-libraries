package com.taisau.android.http.client.plugins

import com.taisau.android.http.client.ApiClientConfig
import com.taisau.android.http.client.ApiPlugin
import com.taisau.android.http.client.ApiPluginKey
import com.taisau.android.http.client.InterceptorOrder
import com.taisau.android.http.client.PrioritizedInterceptor
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * 日志插件 — 记录 HTTP 请求和响应内容。
 *
 * 模仿 Ktor 的 Logging 插件。
 *
 * 使用示例：
 * ```kotlin
 * val client = ApiClient {
 *     // 使用内置 HttpLoggingInterceptor
 *     install(Logging) {
 *         level = HttpLoggingInterceptor.Level.BODY
 *         logger = HttpLoggingInterceptor.Logger.DEFAULT
 *     }
 *
 *     // 或完全自定义日志拦截器
 *     install(Logging) {
 *         customInterceptor = Interceptor { chain ->
 *             val request = chain.request()
 *             Log.d("HTTP", "${request.method} ${request.url}")
 *             val response = chain.proceed(request)
 *             Log.d("HTTP", "${response.code} ${response.request.url}")
 *             response
 *         }
 *     }
 * }
 * ```
 */
object Logging : ApiPlugin<Logging.Config> {

    override val key = ApiPluginKey<Config>("Logging")

    data class Config(
        /** 日志级别，默认仅记录请求行 */
        val level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC,
        /** 自定义日志输出，默认使用 [HttpLoggingInterceptor.Logger.DEFAULT] */
        val logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
        /**
         * 完全自定义日志拦截器。
         * 设置此值后将跳过默认的 [HttpLoggingInterceptor]，使用你提供的拦截器。
         */
        val customInterceptor: Interceptor? = null,
    )

    override fun createConfig() = Config()

    override fun ApiClientConfig.prepare(config: Config) {
        val interceptor = config.customInterceptor ?: HttpLoggingInterceptor(config.logger).apply {
            level = config.level
        }
        interceptors += PrioritizedInterceptor(interceptor, InterceptorOrder.LOGGING)
    }
}
