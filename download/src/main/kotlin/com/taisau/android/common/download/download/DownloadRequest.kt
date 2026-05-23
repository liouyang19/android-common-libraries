package com.taisau.android.common.download.download

import com.taisau.android.common.download.DownloadPriority
import java.security.MessageDigest

/**
 * 下载请求参数。
 *
 * 通过 [calculateDownloadId] 生成唯一 ID（url+filePath+fileName 的 MD5），
 * 用于[DownloadManager][com.taisau.android.common.download.DownloadManager] 的任务去重和断点续传。
 *
 * @property url        下载地址
 * @property fileName   保存的文件名
 * @property filePath   保存的目录路径
 * @property headers    自定义请求头
 * @property priority   下载优先级（数字越大越优先）
 * @property id         请求标识（仅透传，引擎不关心）
 * @property tag        分组标签
 * @property method     请求方法（默认 GET）
 * @property connectTimeout 连接超时（毫秒）
 * @property readTimeout    读取超时（毫秒）
 */
data class DownloadRequest(
    val url: String,
    val fileName: String = "",
    val filePath: String = "",
    val headers: Map<String, String> = emptyMap(),
    val priority: DownloadPriority = DownloadPriority.NORMAL,
    val id: String = "",
    val tag: String = "",
    val method: String = "GET",
    val connectTimeout: Int = 15_000,
    val readTimeout: Int = 15_000,
) {
    /**
     * 计算此请求的唯一下载 ID。
     *
     * 使用 MD5(url + filePath + fileName)，
     * 同一 URL 保存到同一路径会返回相同的 ID，自动实现断点续传。
     */
    fun calculateDownloadId(): String {
        val input = "$url$filePath$fileName"
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun calculateDownloadId(url: String, filePath: String, fileName: String): String {
            val input = "$url$filePath$fileName"
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
