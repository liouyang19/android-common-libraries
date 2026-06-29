package com.taisau.android.http.client.plugins

import com.taisau.android.http.client.ApiClientConfig
import com.taisau.android.http.client.ApiPlugin
import com.taisau.android.http.client.ApiPluginKey
import com.taisau.android.http.client.InterceptorOrder
import com.taisau.android.http.client.PrioritizedInterceptor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

// ═══════════════════════════════════════════════
// 加密接口定义
// ═══════════════════════════════════════════════

/**
 * 请求体加密器。
 *
 * 实现此接口将原始请求字节加密为密文字节，并可添加自定义请求头。
 *
 * 示例（AES-256-GCM）：
 * ```kotlin
 * val encryptor = RequestEncryptor { data, contentType, headers ->
 *     headers["X-Encrypt-Algo"] = "AES-256-GCM"
 *     headers["X-Encrypt-Key-Id"] = keyId
 *     aesEncrypt(data, secretKey)
 * }
 * ```
 */
fun interface RequestEncryptor {

    /**
     * 加密请求体。
     *
     * @param data 原始请求体字节
     * @param originalContentType 原始 Content-Type，如 "application/json"
     * @param headers 可变请求头映射，可在此添加加密相关头（如 `X-Encrypt-Algorithm`）
     * @return 加密后的字节
     */
    fun encrypt(data: ByteArray, originalContentType: String?, headers: MutableMap<String, String>): ByteArray
}

/**
 * 响应体解密器。
 *
 * 实现此接口将密文响应字节解密为原始字节。
 *
 * 示例：
 * ```kotlin
 * val decryptor = ResponseDecryptor { data, headers ->
 *     val keyId = headers["X-Encrypt-Key-Id"] ?: error("missing key id")
 *     aesDecrypt(data, lookupKey(keyId))
 * }
 * ```
 */
fun interface ResponseDecryptor {

    /**
     * 解密响应体。
     *
     * @param data 原始响应体字节（密文）
     * @param headers 响应头，可从中读取解密所需参数（如密钥 ID、算法）
     * @return 解密后的字节
     */
    fun decrypt(data: ByteArray, headers: Map<String, String>): ByteArray
}

// ═══════════════════════════════════════════════
// Encryption 插件
// ═══════════════════════════════════════════════

/**
 * 请求加密 / 响应解密插件。
 *
 * 通过 OkHttp [Interceptor] 机制，在发送前加密请求体、收到后解密响应体。
 * 加密/解密算法完全由用户通过 [RequestEncryptor] / [ResponseDecryptor] 自定义。
 *
 * ## 使用示例
 *
 * ```kotlin
 * val client = ApiClient {
 *     baseUrl("https://api.example.com")
 *
 *     install(Encryption) {
 *         requestEncryptor = RequestEncryptor { data, contentType, headers ->
 *             headers["X-Encrypt"] = "v1"
 *             encrypt(data, secretKey)
 *         }
 *         responseDecryptor = ResponseDecryptor { data, headers ->
 *             decrypt(data, secretKey)
 *         }
 *     }
 * }
 * ```
 */
object Encryption : ApiPlugin<Encryption.Config> {

    override val key = ApiPluginKey<Config>("Encryption")

    data class Config(
        /** 请求加密器（null = 不加密请求） */
        val requestEncryptor: RequestEncryptor? = null,
        /** 响应解密器（null = 不解密响应） */
        val responseDecryptor: ResponseDecryptor? = null,
        /** 加密后的 Content-Type，默认 "application/octet-stream" */
        val encryptedContentType: String = "application/octet-stream",
        /**
         * 完全自定义的请求加密拦截器，替换默认的 [requestEncryptor] 逻辑。
         * 设置此值后 [requestEncryptor] 不再生效。
         */
        val customRequestInterceptor: Interceptor? = null,
        /**
         * 完全自定义的响应解密拦截器，替换默认的 [responseDecryptor] 逻辑。
         * 设置此值后 [responseDecryptor] 不再生效。
         */
        val customResponseInterceptor: Interceptor? = null,
    )

    override fun createConfig() = Config()

    override fun ApiClientConfig.prepare(config: Config) {
        // 请求加密拦截器（应用拦截器，在重试/重定向之前）
        val requestInterceptor = config.customRequestInterceptor
            ?: config.requestEncryptor?.let { RequestEncryptionInterceptor(it, config.encryptedContentType) }
        if (requestInterceptor != null) {
            interceptors += PrioritizedInterceptor(requestInterceptor, InterceptorOrder.ENCRYPTION)
        }

        // 响应解密拦截器（网络拦截器，获取原始响应体）
        val responseInterceptor = config.customResponseInterceptor
            ?: config.responseDecryptor?.let { ResponseDecryptionInterceptor(it) }
        if (responseInterceptor != null) {
            networkInterceptors += responseInterceptor
        }
    }
}

// ═══════════════════════════════════════════════
// Interceptor 实现
// ═══════════════════════════════════════════════

/**
 * 请求加密拦截器。
 *
 * 工作流程：
 * 1. 读取原始请求体字节
 * 2. 调用 [RequestEncryptor.encrypt] 加密
 * 3. 替换 Content-Type 为 [encryptedContentType]
 * 4. 添加加密器返回的自定义头
 */
internal class RequestEncryptionInterceptor(
    private val encryptor: RequestEncryptor,
    private val encryptedContentType: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val body = request.body ?: return chain.proceed(request)

        // 跳过 GET / HEAD 等无体请求
        val contentType = body.contentType()?.toString()
        val method = request.method
        if (method == "GET" || method == "HEAD") return chain.proceed(request)

        // 读取请求体字节
        val buffer = Buffer()
        body.writeTo(buffer)
        val originalBytes = buffer.readByteArray()

        if (originalBytes.isEmpty()) return chain.proceed(request)

        // 执行加密
        val encryptHeaders = mutableMapOf<String, String>()
        val encryptedBytes = encryptor.encrypt(originalBytes, contentType, encryptHeaders)

        // 构建加密后的请求体
        val newMediaType = encryptedContentType.toMediaTypeOrNull()
        val newBody = encryptedBytes.toRequestBody(newMediaType)

        // 构建新请求
        val newRequest = request.newBuilder()
            .method(method, newBody)
            .apply {
                // 移除原始 Content-Type
                if (contentType != null) {
                    header("Content-Type", encryptedContentType)
                }
                // 添加加密头
                encryptHeaders.forEach { (key, value) ->
                    header(key, value)
                }
            }
            .build()

        return chain.proceed(newRequest)
    }
}

/**
 * 响应解密拦截器。
 *
 * 工作流程：
 * 1. 获取原始响应体字节
 * 2. 调用 [ResponseDecryptor.decrypt] 解密
 * 3. 替换 Content-Type 回原始类型（可选）
 * 4. 返回解密后的响应体
 */
internal class ResponseDecryptionInterceptor(
    private val decryptor: ResponseDecryptor,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val response = chain.proceed(chain.request())

        val body = response.body
        val mediaType = body.contentType()

        // 读取响应体字节
        val bytes = body.bytes()

        if (bytes.isEmpty()) return response

        try {
            // 执行解密
            val responseHeaders = response.headers.toMap()
            val decryptedBytes = decryptor.decrypt(bytes, responseHeaders)

            // 构建解密后的响应体
            // 保持原始 Content-Type，因为解密后格式应恢复
            val newBody = decryptedBytes.toResponseBody(mediaType)

            return response.newBuilder()
                .body(newBody)
                .build()
        } catch (e: Exception) {
            // 解密失败，返回原始响应（不中断流程）
            return response
        }
    }
}
