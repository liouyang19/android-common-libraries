package com.taisau.android.common.upgrade

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpgradeManager private constructor(
    private val context: Context,
    private val config: Config,
) {
    data class Config(
        val checkUrl: String,
        val headers: Map<String, String>,
        val connectTimeout: Int,
        val readTimeout: Int,
        val authority: String,
    )
    class Builder(private val context: Context) {
        private var checkUrl: String = ""
        private val headers: MutableMap<String, String> = HashMap()
        private var connectTimeout: Int = 10_000
        private var readTimeout: Int = 10_000
        private var authority: String = "${context.packageName}.fileprovider"

        fun setCheckUrl(url: String) = apply { checkUrl = url }
        fun addHeader(key: String, value: String) = apply { headers[key] = value }
        fun setConnectTimeout(timeout: Int) = apply { connectTimeout = timeout }
        fun setReadTimeout(timeout: Int) = apply { readTimeout = timeout }
        fun setAuthority(authority: String) = apply { this.authority = authority }
        fun build() = UpgradeManager(
            context.applicationContext,
            Config(checkUrl, headers.toMap(), connectTimeout, readTimeout, authority),
        )
    }

    suspend fun check(): Result<UpgradeInfo> = withContext(Dispatchers.IO) {
        if (config.checkUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("checkUrl must be set"))
        }
        try {
            val conn = URL(config.checkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = config.connectTimeout
            conn.readTimeout = config.readTimeout
            conn.requestMethod = "GET"
            config.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            Result.success(UpgradeInfo.fromJson(JSONObject(json)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun download(
        info: UpgradeInfo,
        title: String = "下载更新",
        description: String = "正在下载新版本 ${info.versionName}",
        notificationVisibility: Int = DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
    ): Long {
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl)).apply {
            setTitle(title)
            setDescription(description)
            setNotificationVisibility(notificationVisibility)
            setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, "update_${info.versionName}.apk"
            )
            config.headers.forEach { (k, v) -> addRequestHeader(k, v) }
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    fun getProgress(downloadId: Long): DownloadProgress {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use { c ->
            if (c.moveToFirst()) {
                val downloaded = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))
                val uri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                return DownloadProgress(downloaded, total, status, uri)
            }
        }
        return DownloadProgress.failed()
    }

    fun install(downloadId: Long) {
        val progress = getProgress(downloadId)
        if (!progress.isSuccessful) return
        val fileUri = Uri.parse(progress.localUri)
        val installUri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(context, config.authority, File(fileUri.path!!))
        } else {
            fileUri
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(installUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun cancel(downloadId: Long) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.remove(downloadId)
    }
}

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: Int,
    val localUri: String?,
) {
    val progress: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    val isSuccessful: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
    val isRunning: Boolean get() = status == DownloadManager.STATUS_RUNNING
    val isPending: Boolean get() = status == DownloadManager.STATUS_PENDING

    companion object {
        fun failed() = DownloadProgress(0, 0, DownloadManager.STATUS_FAILED, null)
    }
}
