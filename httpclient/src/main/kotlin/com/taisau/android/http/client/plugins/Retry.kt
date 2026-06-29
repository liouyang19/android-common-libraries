package com.taisau.android.http.client.plugins

import com.taisau.android.http.client.ApiClientConfig
import com.taisau.android.http.client.ApiPlugin
import com.taisau.android.http.client.ApiPluginKey
import com.taisau.android.http.client.InterceptorOrder
import com.taisau.android.http.client.PrioritizedInterceptor
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

/**
 * 重试插件 — 请求失败时自动重试。
 *
 * 支持：
 * - **固定间隔**：每次重试等待固定时长
 * - **指数退避**：每次重试等待时间翻倍（`backoffMultiplier = 2`）
 * - **随机抖动**：在等待时间上增加随机波动，防止惊群效应
 * - **上限保护**：最长等待时间不超过 [Config.maxDelayMs]
 *
 * 使用示例：
 * ```kotlin
 * val client = ApiClient {
 *     // 固定间隔重试
 *     install(Retry) {
 *         maxRetries = 3
 *         delayMs = 1000L
 *     }
 *
 *     // 指数退避 + 抖动
 *     install(Retry) {
 *         maxRetries = 5
 *         delayMs = 500L
 *         backoffMultiplier = 2.0     // 500 → 1000 → 2000 → 4000 → 8000
 *         maxDelayMs = 10_000L
 *         jitter = 0.2                // ±20% 随机抖动
 *     }
 * }
 * ```
 */
object Retry : ApiPlugin<Retry.Config> {

    override val key = ApiPluginKey<Config>("Retry")

    data class Config(
        /** 最大重试次数（0 = 不重试） */
        val maxRetries: Int = 3,
        /** 首次重试间隔（毫秒） */
        val delayMs: Long = 1000L,
        /**
         * 指数退避倍数。
         * - 1.0 = 固定间隔（默认）
         * - 2.0 = 每次翻倍
         *
         * 每次重试的实际延迟 = delayMs × backoffMultiplier^attempt
         */
        val backoffMultiplier: Double = 1.0,
        /** 最大重试延迟（毫秒），0 = 不限制，默认 30 秒 */
        val maxDelayMs: Long = 30_000L,
        /**
         * 随机抖动比例（0.0 ~ 1.0）。
         * 0.2 = 在计算延迟上 ±20% 随机波动，防止突发流量下集体重试。
         */
        val jitter: Double = 0.0,
        /** 网络异常时是否重试（IOException） */
        val retryOnNetworkError: Boolean = true,
        /** 非 2xx HTTP 状态码时是否重试 */
        val retryOnHttpError: Boolean = false,
        /** 自定义重试判断，返回 true 则重试 */
        val retryIf: ((Response) -> Boolean)? = null,
        /**
         * 完全自定义重试拦截器。
         * 设置此值后将跳过默认的重试逻辑。
         */
        val customInterceptor: Interceptor? = null,
    ) {
        /** 计算第 [attempt] 次重试的实际延迟（含指数退避 + 抖动） */
        internal fun computeDelay(attempt: Int): Long {
            val base = (delayMs * Math.pow(backoffMultiplier, attempt.toDouble())).toLong()
            val capped = if (maxDelayMs > 0) min(base, maxDelayMs) else base
            return if (jitter > 0.0) {
                val offset = (capped * jitter).toLong()
                capped + Random.nextLong(-offset, offset + 1)
            } else {
                capped
            }.coerceAtLeast(0)
        }
    }

    override fun createConfig() = Config()

    override fun ApiClientConfig.prepare(config: Config) {
        val interceptor = config.customInterceptor
            ?: if (config.maxRetries > 0) RetryInterceptor(config) else null
        if (interceptor != null) {
            interceptors += PrioritizedInterceptor(interceptor, InterceptorOrder.RETRY)
        }
    }
}

internal class RetryInterceptor(
    private val config: Retry.Config,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: Exception? = null

        for (attempt in 0..config.maxRetries) {
            try {
                val response = chain.proceed(request)

                if (response.isSuccessful) return response
                if (attempt == config.maxRetries) return response

                val shouldRetry = config.retryIf?.invoke(response)
                    ?: config.retryOnHttpError

                if (shouldRetry) {
                    response.close()
                    sleep(attempt)
                } else {
                    return response
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt == config.maxRetries) throw e
                if (config.retryOnNetworkError) {
                    sleep(attempt)
                } else {
                    throw e
                }
            }
        }

        throw lastException ?: IOException("Max retries reached")
    }

    private fun sleep(attempt: Int) {
        val delay = config.computeDelay(attempt)
        if (delay > 0) Thread.sleep(delay)
    }
}
