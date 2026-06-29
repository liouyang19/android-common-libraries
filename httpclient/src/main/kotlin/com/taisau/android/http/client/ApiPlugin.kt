package com.taisau.android.http.client

import okhttp3.Interceptor
import retrofit2.Converter

/**
 * API 插件接口，模仿 Ktor 的 [install] 插件系统。
 *
 * 每个插件封装一类功能（日志、认证、重试等），通过 [ApiClientConfig.install] 注册，
 * 自动向 OkHttpClient / Retrofit 注入拦截器或转换器。
 *
 * ## 内置插件
 *
 * | 插件 | 功能 |
 * |------|------|
 * | [Logging] | HTTP 请求/响应日志 |
 * | [Auth] | Bearer Token 认证 + 自动刷新 |
 * | [Retry] | 请求失败自动重试 |
 * | [ContentNegotiation] | JSON 序列化配置 |
 *
 * ## 自定义插件示例
 *
 * ```kotlin
 * data class CacheConfig(val maxAgeSeconds: Int = 60)
 *
 * object CachePlugin : ApiPlugin<CacheConfig> {
 *     override val key = ApiPluginKey<CacheConfig>("CachePlugin")
 *     override fun createConfig() = CacheConfig()
 *     override fun ApiClientConfig.prepare(config: CacheConfig) {
 *         interceptors += Interceptor { chain ->
 *             chain.proceed(chain.request()).newBuilder()
 *                 .header("Cache-Control", "public, max-age=${config.maxAgeSeconds}")
 *                 .build()
 *         }
 *     }
 * }
 * ```
 */
interface ApiPlugin<T : Any> {
    /** 插件的唯一标识键 */
    val key: ApiPluginKey<T>

    /** 创建插件的默认配置实例 */
    fun createConfig(): T

    /**
     * 安装插件到 [ApiClientConfig]。
     *
     * 在此方法中可以通过 [ApiClientConfig.interceptors]、[ApiClientConfig.converterFactories]
     * 等向 OkHttpClient 或 Retrofit 注入能力。
     */
    fun ApiClientConfig.prepare(config: T)
}

/**
 * 插件键，用于唯一标识 [ApiPlugin]。
 *
 * @param name 可选的插件名称（仅便于调试）
 */
class ApiPluginKey<T : Any>(val name: String = "ApiPlugin")
