package com.taisau.android.http.client.plugins

import com.taisau.android.http.client.ApiClientConfig
import com.taisau.android.http.client.ApiPlugin
import com.taisau.android.http.client.ApiPluginKey
import com.taisau.android.http.client.InterceptorOrder
import com.taisau.android.http.client.PrioritizedInterceptor
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 认证插件 — 自动注入 Bearer Token 并支持 401 后刷新。
 *
 * 模仿 Ktor 的 BearerAuth 插件。
 *
 * 使用示例：
 * ```kotlin
 * val client = ApiClient {
 *     // 默认 Bearer Token 模式
 *     install(Auth) {
 *         tokenProvider { SessionManager.token }
 *         onTokenExpired = { SessionManager.refresh(); true }
 *     }
 *
 *     // 或完全自定义认证逻辑
 *     install(Auth) {
 *         customInterceptor = Interceptor { chain ->
 *             val request = chain.request().newBuilder()
 *                 .header("X-API-Key", getMyApiKey())
 *                 .build()
 *             chain.proceed(request)
 *         }
 *     }
 * }
 * ```
 */
object Auth : ApiPlugin<Auth.Config> {

    override val key = ApiPluginKey<Config>("Auth")

    data class Config(
        /** Token 提供者，返回 Bearer Token 或 null */
        val tokenProvider: () -> String? = { null },
        /**
         * Token 过期回调，返回 true 表示刷新成功。
         * 仅在 statusCode == 401 时触发。
         */
        val onTokenExpired: (suspend () -> Boolean)? = null,
        /** 刷新后的新 Token 提供者 */
        val refreshedTokenProvider: (() -> String?)? = null,
        /** 自定义认证头格式，默认 "Bearer" */
        val authScheme: String = "Bearer",
        /**
         * 完全自定义认证拦截器。
         * 设置此值后将跳过默认的 Bearer Token 逻辑，使用你提供的拦截器。
         */
        val customInterceptor: Interceptor? = null,
    )

    override fun createConfig() = Config()

    override fun ApiClientConfig.prepare(config: Config) {
        val interceptor = config.customInterceptor ?: AuthInterceptor(config)
        interceptors += PrioritizedInterceptor(interceptor, InterceptorOrder.AUTH)
    }
}

/** Auth 插件的 OkHttp Interceptor 实现 */
internal class AuthInterceptor(
    private val config: Auth.Config,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = config.tokenProvider()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "${config.authScheme} $token")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)

        // 401 且配置了刷新逻辑 → 尝试刷新
        if (response.code == 401 && config.onTokenExpired != null) {
            response.close()
            val refreshCallback = config.onTokenExpired
            val refreshed = runBlocking { refreshCallback() }
            if (refreshed) {
                val newToken = config.refreshedTokenProvider?.invoke()
                if (newToken != null) {
                    val retryRequest = chain.request().newBuilder()
                        .header("Authorization", "${config.authScheme} $newToken")
                        .build()
                    return chain.proceed(retryRequest)
                }
            }
        }

        return response
    }
}
