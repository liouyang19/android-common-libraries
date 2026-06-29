package com.taisau.android.http.client

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * [CallAdapter.Factory] — 自动将 Retrofit 返回值封装为 [ApiResult]。
 *
 * 安装后在 API 接口中直接返回 [ApiResult]，框架自动处理成功/失败/异常。
 *
 * ## 用法
 *
 * ```kotlin
 * // 1. 注册
 * val client = ApiClient {
 *     baseUrl("https://api.example.com")
 *     addCallAdapterFactory(ApiResultCallAdapterFactory())
 * }
 *
 * // 2. 接口定义
 * interface UserApi {
 *     @GET("users")
 *     suspend fun listUsers(): ApiResult<List<User>>
 *
 *     @GET("users/{id}")
 *     fun getUser(@Path("id") id: Long): ApiResult<User>  // 同步也支持
 * }
 *
 * // 3. 使用
 * val api = client.create<UserApi>()
 * when (val result = api.listUsers()) {
 *     is ApiResult.Success -> handle(result.data)
 *     is ApiResult.Error -> showError(result.message)
 *     is ApiResult.Exception -> logError(result.throwable)
 *     is ApiResult.Loading -> showLoading()  // suspend 函数不会走到 Loading
 * }
 * ```
 *
 * ### 内部行为
 *
 * | 情况 | 包装结果 |
 * |------|---------|
 * | HTTP 200 + body | `ApiResult.Success(data)` |
 * | HTTP 200 + null body | `ApiResult.Error(200, "body is null")` |
 * | HTTP 4xx/5xx | `ApiResult.Error(code, message)` |
 * | 网络异常 | `ApiResult.Exception(IOException)` |
 * | 序列化/解析异常 | `ApiResult.Exception(Throwable)` |
 */
class ApiResultCallAdapterFactory : CallAdapter.Factory() {

    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *>? {
        // 只处理 ApiResult<T> 类型的返回值
        val rawType = getRawType(returnType)
        if (rawType != ApiResult::class.java) return null

        // 获取泛型参数 T（ApiResult<T> 中的 T）
        val successType = if (returnType is ParameterizedType) {
            getParameterUpperBound(0, returnType)
        } else {
            // 裸 ApiResult 无泛型参数时使用 Any
            Any::class.java
        }

        return ApiResultCallAdapter<Any>(successType)
    }
}

/**
 * 将 [Call] 的执行结果包装为 [ApiResult]。
 */
internal class ApiResultCallAdapter<T>(
    private val successType: Type,
) : CallAdapter<T, ApiResult<T>> {

    override fun responseType(): Type = successType

    override fun adapt(call: Call<T>): ApiResult<T> {
        return try {
            val response = call.execute()

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    @Suppress("UNCHECKED_CAST")
                    ApiResult.Success(body as T)
                } else {
                    ApiResult.Error(response.code(), "Response body is null")
                }
            } else {
                // 尝试读取错误体
                val errorMessage = try {
                    response.errorBody()?.string() ?: response.message()
                } catch (_: Exception) {
                    response.message()
                }
                ApiResult.Error(response.code(), errorMessage)
            }
        } catch (e: IOException) {
            ApiResult.Exception(e)
        } catch (e: Exception) {
            ApiResult.Exception(e)
        }
    }
}
