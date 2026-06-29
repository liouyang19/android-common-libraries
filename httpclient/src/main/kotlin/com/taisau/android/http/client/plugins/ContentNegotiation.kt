package com.taisau.android.http.client.plugins

import com.taisau.android.http.client.ApiClientConfig
import com.taisau.android.http.client.ApiPlugin
import com.taisau.android.http.client.ApiPluginKey
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Converter
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * 内容协商插件 — 配置 JSON 序列化/反序列化。
 *
 * 模仿 Ktor 的 ContentNegotiation 插件，管理 [Converter.Factory]。
 *
 * 使用示例：
 * ```kotlin
 * val client = ApiClient {
 *     // 默认 JSON 配置
 *     install(ContentNegotiation) {
 *         json(Json { ignoreUnknownKeys = true })
 *     }
 *
 *     // 或添加自定义 Converter.Factory
 *     install(ContentNegotiation) {
 *         json(Json { ignoreUnknownKeys = true })
 *         addConverter(MyCustomConverterFactory())
 *     }
 * }
 * ```
 */
object ContentNegotiation : ApiPlugin<ContentNegotiation.Config> {

    override val key = ApiPluginKey<Config>("ContentNegotiation")

    data class Config(
        /** Json 实例，默认开启 ignoreUnknownKeys */
        val json: Json = Json { ignoreUnknownKeys = true },
        /** Content-Type */
        val contentType: String = "application/json",
        /** 额外的 [Converter.Factory]，在 JSON 之后添加 */
        val additionalConverters: List<Converter.Factory> = emptyList(),
    ) {
        /** 添加自定义 [Converter.Factory] */
        fun addConverter(factory: Converter.Factory): Config {
            return copy(additionalConverters = additionalConverters + factory)
        }
    }

    override fun createConfig() = Config()

    override fun ApiClientConfig.prepare(config: Config) {
        val mediaType = config.contentType.toMediaType()
        converterFactories += config.json.asConverterFactory(mediaType)
        converterFactories += config.additionalConverters
    }
}
