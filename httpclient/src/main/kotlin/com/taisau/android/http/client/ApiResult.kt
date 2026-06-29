package com.taisau.android.http.client

sealed class ApiResult<out T> {

    data class Success<T>(val data: T) : ApiResult<T>()

    data class Error(val code: Int = -1, val message: String) : ApiResult<Nothing>()

    data class Exception(val throwable:  Throwable) : ApiResult<Nothing>()

    data object Loading : ApiResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error || this is Exception
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> default
    }

    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Exception -> this
        is Loading -> Loading
    }

    fun <R> flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Exception -> this
        is Loading -> Loading
    }

    fun onSuccess(action: (T) -> Unit): ApiResult<T> {
        if (this is Success) action(data)
        return this
    }

    fun onError(action: (Error) -> Unit): ApiResult<T> {
        if (this is Error) action(this)
        return this
    }

    fun onException(action: (Exception) -> Unit): ApiResult<T> {
        if (this is Exception) action(this)
        return this
    }

    fun onFailure(action: (ApiResult<Nothing>) -> Unit): ApiResult<T> {
        if (this is Error || this is Exception) action(this)
        return this
    }

    companion object {
        fun <T> success(data: T): ApiResult<T> = Success(data)

        fun error(code: Int = -1, message: String): ApiResult<Nothing> = Error(code, message)

        fun exception(throwable: kotlin.Throwable): ApiResult<Nothing> = Exception(throwable)

        fun loading(): ApiResult<Nothing> = Loading
    }
}
