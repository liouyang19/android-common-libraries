package com.taisau.android.common.retrofit

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object RetrofitClient {

    private val cache = mutableMapOf<String, Retrofit>()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
        prettyPrint = false
    }

    fun getRetrofit(config: RetrofitConfig): Retrofit {
        return cache.getOrPut(config.baseUrl) { createRetrofit(config) }
    }

    fun createRetrofit(config: RetrofitConfig): Retrofit {
        val client = createOkHttpClient(config)
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    fun createOkHttpClient(config: RetrofitConfig): OkHttpClient {
        return OkHttpClient.Builder().apply {
            connectTimeout(config.connectTimeout, config.timeoutUnit)
            readTimeout(config.readTimeout, config.timeoutUnit)
            writeTimeout(config.writeTimeout, config.timeoutUnit)

            if (config.isDebug) {
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = config.logLevel
                }
                addInterceptor(loggingInterceptor)
            }

            if (config.headers.isNotEmpty()) {
                addInterceptor { chain ->
                    val request = chain.request().newBuilder().apply {
                        config.headers.forEach { (key, value) ->
                            header(key, value)
                        }
                    }.build()
                    chain.proceed(request)
                }
            }

            config.tokenProvider?.let { provider ->
                addInterceptor { chain ->
                    val token = provider()
                    val request = if (token != null) {
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    } else {
                        chain.request()
                    }
                    chain.proceed(request)
                }
            }

            config.interceptors.forEach { addInterceptor(it) }
            config.networkInterceptors.forEach { addNetworkInterceptor(it) }

            val dispatcher = Dispatcher()
            dispatcher.maxRequests = config.maxParallelRequests
            dispatcher.maxRequestsPerHost = config.maxRequestsPerHost
            this.dispatcher(dispatcher)
        }.build()
    }

    fun <T> create(config: RetrofitConfig, serviceClass: Class<T>): T {
        return getRetrofit(config).create(serviceClass)
    }

    inline fun <reified T> create(config: RetrofitConfig): T {
        return create(config, T::class.java)
    }

    fun clearCache() {
        cache.clear()
    }

    fun removeCache(baseUrl: String) {
        cache.remove(baseUrl)
    }
}
