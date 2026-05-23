package com.taisau.android.common.upgrade.strategy

import com.taisau.android.common.upgrade.UpgradeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * [CheckStrategy] 的默认实现。
 *
 * 使用 [HttpURLConnection] 发起 GET 请求，在 [Dispatchers.IO] 执行。
 * 超时和请求头由具体实现硬编码，使用者如有自定义需求应自行实现 [CheckStrategy]。
 *
 * @param context Application Context
 */
class DefaultCheckStrategy(
    @Suppress("UNUSED_PARAMETER")
    private val context: android.content.Context,
) : CheckStrategy {

    override suspend fun check(checkUrl: String): Result<UpgradeInfo> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(checkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            Result.success(UpgradeInfo.fromJson(JSONObject(json)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
