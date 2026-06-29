package com.taisau.android.http.client.plugins

import com.taisau.android.http.client.ApiClientConfig
import com.taisau.android.http.client.ApiPlugin
import com.taisau.android.http.client.ApiPluginKey
import com.taisau.android.http.client.InterceptorOrder
import com.taisau.android.http.client.PrioritizedInterceptor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Mock 插件 — 拦截请求返回模拟数据，用于测试 / 开发调试。
 *
 * 通过 URL 路径匹配 + HTTP 方法匹配拦截请求，返回预设的响应。
 * 支持模拟网络延迟和随机错误，便于测试各种异常场景。
 *
 * 使用示例：
 * ```kotlin
 * val client = ApiClient {
 *     install(Mock) {
 *         // 精确匹配
 *         mock("GET", "/users") {
 *             code = 200
 *             body = """[{"id":1,"name":"Alice"}]"""
 *         }
 *
 *         // 前缀匹配
 *         mockGet("/users/") {
 *             code = 200
 *             body = """{"id":1,"name":"Alice"}"""
 *         }
 *
 *         // POST 模拟
 *         mockPost("/login") {
 *             code = 200
 *             body = """{"token":"mock-token"}"""
 *         }
 *
 *         // 模拟网络延迟 500ms
 *         delayMs = 500L
 *
 *         // 30% 概率模拟网络错误
 *         failureRate = 0.3
 *     }
 * }
 * ```
 */
object Mock : ApiPlugin<Mock.Config> {

    override val key = ApiPluginKey<Config>("Mock")

    data class Config(
        /** 模拟规则列表 */
        internal val rules: MutableList<MockRule> = mutableListOf(),
        /** 模拟网络延迟（毫秒），默认 0 */
        var delayMs: Long = 0L,
        /** 模拟失败概率（0.0 ~ 1.0），默认 0 */
        var failureRate: Double = 0.0,
    ) {
        /**
         * 添加模拟规则。
         * @param method HTTP 方法（GET / POST 等）
         * @param urlPattern URL 匹配模式（支持前缀匹配，以 * 结尾表示前缀匹配）
         * @param configure 响应配置
         */
        fun mock(method: String, urlPattern: String, configure: MockResponse.() -> Unit = {}) {
            val response = MockResponse().apply(configure)
            rules.add(MockRule(method, urlPattern, response))
        }

        /** 模拟 GET 请求的快捷方法 */
        fun mockGet(urlPattern: String, configure: MockResponse.() -> Unit = {}) {
            mock("GET", urlPattern, configure)
        }

        /** 模拟 POST 请求的快捷方法 */
        fun mockPost(urlPattern: String, configure: MockResponse.() -> Unit = {}) {
            mock("POST", urlPattern, configure)
        }

        /** 模拟 PUT 请求的快捷方法 */
        fun mockPut(urlPattern: String, configure: MockResponse.() -> Unit = {}) {
            mock("PUT", urlPattern, configure)
        }

        /** 模拟 DELETE 请求的快捷方法 */
        fun mockDelete(urlPattern: String, configure: MockResponse.() -> Unit = {}) {
            mock("DELETE", urlPattern, configure)
        }

        /** 清空所有模拟规则 */
        fun clear() {
            rules.clear()
        }
    }

    override fun createConfig() = Config()

    override fun ApiClientConfig.prepare(config: Config) {
        if (config.rules.isNotEmpty()) {
            interceptors += PrioritizedInterceptor(
                MockInterceptor(config),
                InterceptorOrder.MOCK,
            )
        }
    }
}

// ═══════════════════════════════════════════════
// 模型定义
// ═══════════════════════════════════════════════

/** 模拟响应配置 */
data class MockResponse(
    /** HTTP 状态码，默认 200 */
    var code: Int = 200,
    /** 响应体（字符串） */
    var body: String = "",
    /** Content-Type，默认 "application/json" */
    var contentType: String = "application/json",
    /** 自定义响应头 */
    val headers: MutableMap<String, String> = mutableMapOf(),
    /** 响应体字节（优先级高于 [body]） */
    var bodyBytes: ByteArray? = null,
)

/** 模拟规则 */
data class MockRule(
    val method: String,
    val urlPattern: String,
    val response: MockResponse,
) {
    /** 是否匹配请求 */
    fun matches(request: Request): Boolean {
        if (!request.method.equals(method, ignoreCase = true)) return false
        val url = request.url.encodedPath
        return when {
            urlPattern.endsWith("*") -> url.startsWith(urlPattern.removeSuffix("*"))
            else -> url == urlPattern
        }
    }
}

// ═══════════════════════════════════════════════
// Mock 拦截器
// ═══════════════════════════════════════════════

internal class MockInterceptor(
    private val config: Mock.Config,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // 查找匹配的模拟规则
        val rule = config.rules.firstOrNull { it.matches(request) }
            ?: return chain.proceed(request) // 不匹配 → 走真实网络

        // 模拟失败
        if (config.failureRate > 0 && Math.random() < config.failureRate) {
            throw java.io.IOException("Mock: simulated network failure")
        }

        // 模拟延迟
        if (config.delayMs > 0) {
            Thread.sleep(config.delayMs)
        }

        // 构造模拟响应
        val mock = rule.response
        val body = mock.bodyBytes
            ?: mock.body.toByteArray(Charsets.UTF_8)

        val contentType = mock.contentType.toMediaTypeOrNull()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(mock.code)
            .message(getStatusMessage(mock.code))
            .apply {
                mock.headers.forEach { (k, v) -> header(k, v) }
                header("Content-Type", mock.contentType)
                header("Content-Length", body.size.toString())
            }
            .body(body.toResponseBody(contentType))
            .build()
    }

    private fun getStatusMessage(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        301 -> "Moved Permanently"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        else -> "Mock"
    }
}
