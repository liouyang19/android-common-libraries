package com.taisau.android.common.retrofit

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

object ApiInterceptor {

    fun loggingInterceptor(level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply { this.level = level }
    }

    fun headerInterceptor(headers: Map<String, String>): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()
            chain.proceed(request)
        }
    }

    fun authInterceptor(tokenProvider: () -> String?): Interceptor {
        return Interceptor { chain ->
            val token = tokenProvider()
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

    fun authInterceptorWithRefresh(
        tokenProvider: () -> String?,
        onTokenExpired: suspend () -> Boolean,
        newTokenProvider: () -> String?
    ): Interceptor {
        return Interceptor { chain ->
            val token = tokenProvider()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            val response = chain.proceed(request)
            if (response.code == 401) {
                response.close()
                val refreshed = kotlinx.coroutines.runBlocking { onTokenExpired() }
                if (refreshed) {
                    val newToken = newTokenProvider()
                    val retryRequest = chain.request().newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    return@Interceptor chain.proceed(retryRequest)
                }
            }
            response
        }
    }

    fun userAgentInterceptor(userAgent: String): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .build()
            chain.proceed(request)
        }
    }

    fun retryInterceptor(maxRetries: Int = 3): Interceptor {
        return Interceptor { chain ->
            var request = chain.request()
            var response: Response? = null
            var exception: Exception? = null
            for (i in 0..maxRetries) {
                try {
                    response = chain.proceed(request)
                    if (response.isSuccessful) break
                } catch (e: Exception) {
                    exception = e
                    if (i == maxRetries) throw e
                }
            }
            response ?: throw exception ?: Exception("Max retries reached")
        }
    }

    fun networkCacheInterceptor(cacheControl: String = "public, max-age=60"): Interceptor {
        return Interceptor { chain ->
            val response = chain.proceed(chain.request())
            response.newBuilder()
                .header("Cache-Control", cacheControl)
                .removeHeader("Pragma")
                .build()
        }
    }
}
