package com.taisau.android.common.retrofit

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

data class RetrofitConfig(
    val baseUrl: String,
    val connectTimeout: Long = 30L,
    val readTimeout: Long = 30L,
    val writeTimeout: Long = 30L,
    val timeoutUnit: TimeUnit = TimeUnit.SECONDS,
    val isDebug: Boolean = false,
    val logLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY,
    val headers: Map<String, String> = emptyMap(),
    val tokenProvider: (() -> String?)? = null,
    val interceptors: List<Interceptor> = emptyList(),
    val networkInterceptors: List<Interceptor> = emptyList(),
    val retryCount: Int = 0,
    val retryDelayMs: Long = 1000L,
    val maxParallelRequests: Int = 64,
    val maxRequestsPerHost: Int = 5,
) {
    class Builder {
        private var baseUrl: String = ""
        private var connectTimeout: Long = 30L
        private var readTimeout: Long = 30L
        private var writeTimeout: Long = 30L
        private var timeoutUnit: TimeUnit = TimeUnit.SECONDS
        private var isDebug: Boolean = false
        private var logLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY
        private val headers: MutableMap<String, String> = mutableMapOf()
        private var tokenProvider: (() -> String?)? = null
        private val interceptors: MutableList<Interceptor> = mutableListOf()
        private val networkInterceptors: MutableList<Interceptor> = mutableListOf()
        private var retryCount: Int = 0
        private var retryDelayMs: Long = 1000L
        private var maxParallelRequests: Int = 64
        private var maxRequestsPerHost: Int = 5

        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun connectTimeout(timeout: Long) = apply { this.connectTimeout = timeout }
        fun readTimeout(timeout: Long) = apply { this.readTimeout = timeout }
        fun writeTimeout(timeout: Long) = apply { this.writeTimeout = timeout }
        fun timeoutUnit(unit: TimeUnit) = apply { this.timeoutUnit = unit }
        fun debug(debug: Boolean) = apply { this.isDebug = debug }
        fun logLevel(level: HttpLoggingInterceptor.Level) = apply { this.logLevel = level }
        fun header(key: String, value: String) = apply { this.headers[key] = value }
        fun headers(map: Map<String, String>) = apply { this.headers.putAll(map) }
        fun tokenProvider(provider: () -> String?) = apply { this.tokenProvider = provider }
        fun interceptor(interceptor: Interceptor) = apply { this.interceptors.add(interceptor) }
        fun networkInterceptor(interceptor: Interceptor) = apply { this.networkInterceptors.add(interceptor) }
        fun retryCount(count: Int) = apply { this.retryCount = count }
        fun retryDelayMs(delay: Long) = apply { this.retryDelayMs = delay }
        fun maxParallelRequests(max: Int) = apply { this.maxParallelRequests = max }
        fun maxRequestsPerHost(max: Int) = apply { this.maxRequestsPerHost = max }

        fun build(): RetrofitConfig {
            require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
            return RetrofitConfig(
                baseUrl = baseUrl,
                connectTimeout = connectTimeout,
                readTimeout = readTimeout,
                writeTimeout = writeTimeout,
                timeoutUnit = timeoutUnit,
                isDebug = isDebug,
                logLevel = logLevel,
                headers = headers.toMap(),
                tokenProvider = tokenProvider,
                interceptors = interceptors.toList(),
                networkInterceptors = networkInterceptors.toList(),
                retryCount = retryCount,
                retryDelayMs = retryDelayMs,
                maxParallelRequests = maxParallelRequests,
                maxRequestsPerHost = maxRequestsPerHost,
            )
        }
    }

    companion object {
        inline fun build(block: Builder.() -> Unit): RetrofitConfig {
            return Builder().apply(block).build()
        }
    }
}
