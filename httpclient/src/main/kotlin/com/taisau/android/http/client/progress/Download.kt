package com.taisau.android.http.client.progress

import com.taisau.android.http.client.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 下载结果。
 */
sealed class DownloadResult {
    /** 下载成功 */
    data class Success(val file: File) : DownloadResult()
    /** 下载失败 */
    data class Error(val throwable: Throwable) : DownloadResult()
}

/**
 * 通过 [ApiClient] 下载文件，支持进度回调 + 速度限制。
 *
 * 使用 [download] 扩展函数：
 *
 * ```kotlin
 * val result = client.download("/bigfile.zip") {
 *     destFile = File(cacheDir, "bigfile.zip")
 *     speedLimit = 500 * 1024  // 500 KB/s
 *     onProgress = { current, total, speed ->
 *         showProgress(current, total)
 *         showSpeed(speed)
 *     }
 * }
 * ```
 *
 * @param path API 路径（如 "/files/bigfile.zip"），与 [ApiClient.baseUrl] 拼接
 * @param config 下载配置（目标文件、进度回调、限速）
 * @return [DownloadResult]
 */
suspend fun ApiClient.download(
    path: String,
    config: DownloadConfig.() -> Unit = {},
): DownloadResult = withContext(Dispatchers.IO) {
    val cfg = DownloadConfig().apply(config)
    val url = "${this@download.baseUrl.trimEnd('/')}$path"
    val destFile = cfg.destFile ?: File(
        System.getProperty("java.io.tmpdir") ?: "/tmp",
        url.substringAfterLast("/")
    )

    try {
        // 构建独立的 OkHttpClient（下载用，读超时较大）
        val downloadClient = OkHttpClient.Builder()
            .connectTimeout(this@download.config.connectTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)    // 大文件不限时
            .writeTimeout(this@download.config.writeTimeout, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .build()

        val response = downloadClient.newCall(request).execute()

        if (!response.isSuccessful) {
            return@withContext DownloadResult.Error(
                IOException("Download failed: HTTP ${response.code}")
            )
        }

        val body = response.body

        // 包装进度 + 限速
        val listener = ProgressListener { read, total, speed ->
            cfg.onProgress?.invoke(read, total, speed)
        }
        val progressBody = ProgressResponseBody(body, listener, cfg.speedLimit)

        // 写入文件
        destFile.parentFile?.mkdirs()
        val source = progressBody.source()
        destFile.sink().buffer().use { sink ->
            sink.writeAll(source)
        }

        DownloadResult.Success(destFile)
    } catch (e: Exception) {
        DownloadResult.Error(e)
    }
}
