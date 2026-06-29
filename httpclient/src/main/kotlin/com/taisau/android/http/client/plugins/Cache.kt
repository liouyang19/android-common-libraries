package com.taisau.android.http.client.plugins

import com.taisau.android.http.client.ApiClientConfig
import com.taisau.android.http.client.ApiPlugin
import com.taisau.android.http.client.ApiPluginKey
import com.taisau.android.http.client.InterceptorOrder
import com.taisau.android.http.client.PrioritizedInterceptor
import okhttp3.Cache
import okhttp3.Interceptor
import java.io.File

/**
 * 缓存插件 — HTTP 响应磁盘缓存。
 *
 * 基于 OkHttp 内置的 [Cache]，通过 [okHttp] 定制器设置。
 * 支持强制缓存模式，忽略服务端 `Cache-Control` 头。
 *
 * 使用示例：
 * ```kotlin
 * val client = ApiClient {
 *     install(Cache) {
 *         directory = cacheDir
 *         maxSize = 10 * 1024 * 1024  // 10MB
 *     }
 * }
 * ```
 */
object CachePlugin : ApiPlugin<CachePlugin.Config> {

    override val key = ApiPluginKey<Config>("Cache")

    data class Config(
        /** 缓存目录（必填） */
        val directory: File? = null,
        /** 最大缓存大小（字节），默认 10MB */
        val maxSize: Long = 10 * 1024 * 1024,
        /**
         * 强制缓存模式。
         * true  = 忽略服务端 Cache-Control，所有 GET 请求强制缓存
         * false = 遵循服务端 Cache-Control
         */
        val forceCache: Boolean = false,
        /** 强制缓存时长（秒），仅在 [forceCache] = true 时生效 */
        val maxAgeSeconds: Int = 60,
    )

    override fun createConfig() = Config()

    override fun ApiClientConfig.prepare(config: Config) {
        val dir = config.directory
            ?: throw IllegalArgumentException("Cache directory is required")

        // 通过 okHttp {} 定制器设置 OkHttp Cache
        okHttp {
            cache(Cache(dir, config.maxSize))
        }

        // 强制缓存模式 → 添加 Cache-Control 拦截器
        if (config.forceCache) {
            interceptors += PrioritizedInterceptor(
                ForceCacheInterceptor(config.maxAgeSeconds),
                InterceptorOrder.CACHE,
            )
        }
    }
}

/** 强制缓存拦截器：给所有响应加上 Cache-Control */
internal class ForceCacheInterceptor(
    private val maxAgeSeconds: Int,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()

        // 只缓存 GET 请求
        if (request.method != "GET") {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)

        return response.newBuilder()
            .header("Cache-Control", "public, max-age=$maxAgeSeconds")
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .build()
    }
}
