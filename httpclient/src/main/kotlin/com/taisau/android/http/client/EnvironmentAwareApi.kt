package com.taisau.android.http.client

/**
 * 环境感知的 API 代理（已废弃，[ApiClient] 已内置 URL 切换能力）。
 *
 * ```kotlin
 * // 旧：config.environmentAwareApi<Api>().get()
 * // 新：ApiClient { baseUrl(...) }.create<Api>()
 * ```
 */
@Deprecated(
    message = "ApiClient 已内置 baseUrl 切换能力，无需额外代理",
    replaceWith = ReplaceWith(
        "ApiClient.create()",
        "com.taisau.android.http.client.ApiClient",
    ),
)
class EnvironmentAwareApi<T : Any>(
    private val config: RetrofitConfig,
    private val serviceClass: Class<T>,
) {
    private var cachedApi: T? = null

    fun get(): T {
        if (cachedApi == null) {
            cachedApi = RetrofitClient.create(config, serviceClass)
        }
        @Suppress("UNCHECKED_CAST")
        return cachedApi as T
    }

    fun clearCache() {
        cachedApi = null
    }
}

@Deprecated(
    message = "请直接使用 ApiClient { ... }.create()",
    replaceWith = ReplaceWith(
        "ApiClient.create()",
        "com.taisau.android.http.client.ApiClient",
    ),
)
inline fun <reified T : Any> RetrofitConfig.environmentAwareApi(): EnvironmentAwareApi<T> {
    return EnvironmentAwareApi(this, T::class.java)
}
