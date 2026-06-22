package com.taisau.android.common.retrofit

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.jvm.JvmName
import retrofit2.Response

object ApiCaller {

    suspend fun <T> call(apiCall: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(apiCall())
        } catch (e: kotlin.Exception) {
            ApiResult.Exception(e)
        }
    }

    suspend fun <T> call(
        apiCall: suspend () -> T,
        retryCount: Int,
        retryDelayMs: Long = 1000L,
    ): ApiResult<T> {
        var lastException: kotlin.Exception? = null
        for (i in 0..retryCount) {
            try {
                return ApiResult.Success(apiCall())
            } catch (e: kotlin.Exception) {
                lastException = e
                if (i < retryCount) delay(retryDelayMs)
            }
        }
        return ApiResult.Exception(lastException!!)
    }

    suspend fun <T> execute(apiCall: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val response = apiCall()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(response.code(), "Response body is null")
                }
            } else {
                ApiResult.Error(response.code(), response.message())
            }
        } catch (e: kotlin.Exception) {
            ApiResult.Exception(e)
        }
    }

    suspend fun <T> execute(
        apiCall: suspend () -> Response<T>,
        retryCount: Int,
        retryDelayMs: Long = 1000L,
    ): ApiResult<T> {
        var lastError: ApiResult<Nothing>? = null
        for (i in 0..retryCount) {
            try {
                val response = apiCall()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        return ApiResult.Success(body)
                    }
                }
                lastError = ApiResult.Error(response.code(), response.message())
            } catch (e: kotlin.Exception) {
                lastError = ApiResult.Exception(e)
            }
            if (i < retryCount) delay(retryDelayMs)
        }
        return lastError ?: ApiResult.Error(-1, "Unknown error")
    }

    @JvmName("observeDirect")
    fun <T> observe(apiCall: suspend () -> T): Flow<ApiResult<T>> = flow {
        emit(ApiResult.Loading)
        emit(call(apiCall))
    }

    @JvmName("observeResponse")
    fun <T> observe(apiCall: suspend () -> Response<T>): Flow<ApiResult<T>> = flow {
        emit(ApiResult.Loading)
        emit(execute(apiCall))
    }

    suspend fun <T> executeDeferred(apiCall: Deferred<Response<T>>): ApiResult<T> {
        return try {
            val response = apiCall.await()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(response.code(), "Response body is null")
                }
            } else {
                ApiResult.Error(response.code(), response.message())
            }
        } catch (e: kotlin.Exception) {
            ApiResult.Exception(e)
        }
    }
}
