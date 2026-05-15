package com.taisau.android.common.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class DownloadTask internal constructor(
    private val config: DownloadManager.Config,
    val url: String,
    val fileName: String,
    private val listener: DownloadListener?,
) {
    private val _info = MutableStateFlow(
        DownloadInfo(url = url, fileName = fileName, status = DownloadStatus.QUEUED),
    )
    val info: StateFlow<DownloadInfo> = _info.asStateFlow()

    private var job: Job? = null
    @Volatile private var paused = false

    internal fun start(scope: CoroutineScope) {
        paused = false
        _info.value = _info.value.copy(status = DownloadStatus.DOWNLOADING)
        listener?.onResume()
        job = scope.launch(Dispatchers.IO) {
            try {
                if (config.chunkCount > 1) {
                    downloadChunked()
                } else {
                    downloadSingle()
                }
            } catch (e: CancellationException) {
                if (!paused) {
                    _info.value = _info.value.copy(status = DownloadStatus.FAILED)
                    listener?.onError(e)
                }
            } catch (e: Exception) {
                _info.value = _info.value.copy(status = DownloadStatus.FAILED)
                listener?.onError(e)
            }
        }
    }

    fun pause() {
        paused = true
        job?.cancel()
        _info.value = _info.value.copy(status = DownloadStatus.PAUSED)
        listener?.onPause()
    }

    fun cancel() {
        paused = true
        job?.cancel()
        cleanupFiles()
        _info.value = _info.value.copy(status = DownloadStatus.FAILED)
    }

    private fun cleanupFiles() {
        File(config.outputDir, "$fileName.tmp").delete()
        for (i in 0 until config.chunkCount) {
            File(config.outputDir, "$fileName.part.$i").delete()
        }
    }

    private suspend fun downloadSingle() = withContext(Dispatchers.IO) {
        val tmpFile = File(config.outputDir, "$fileName.tmp")
        val downloaded = if (tmpFile.exists()) tmpFile.length() else 0L

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = config.connectTimeout
        conn.readTimeout = config.readTimeout
        if (downloaded > 0) {
            conn.setRequestProperty("Range", "bytes=$downloaded-")
        }

        val responseCode = conn.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
            throw IOException("服务器返回 $responseCode")
        }

        val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
        val totalBytes = when {
            isPartial -> {
                val cr = conn.getHeaderField("Content-Range")
                cr?.substringAfter("/")?.toLongOrNull() ?: downloaded
            }
            else -> conn.contentLengthLong + downloaded
        }

        _info.value = _info.value.copy(totalBytes = totalBytes)

        val outputFile = File(config.outputDir, fileName)
        RandomAccessFile(tmpFile, "rw").use { raf ->
            raf.seek(downloaded)
            val input = conn.inputStream
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var lastUpdateMs = System.currentTimeMillis()
            var lastBytes = downloaded

            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (paused || !isActive) {
                    _info.value = _info.value.copy(status = DownloadStatus.PAUSED)
                    return@withContext
                }
                raf.write(buffer, 0, bytesRead)
                val currentDownloaded = downloaded + raf.filePointer
                val now = System.currentTimeMillis()

                if (now - lastUpdateMs > 300) {
                    val speed = ((currentDownloaded - lastBytes) * 1000) / (now - lastUpdateMs)
                    _info.value = _info.value.copy(
                        downloadedBytes = currentDownloaded, speed = speed,
                    )
                    listener?.onProgress(currentDownloaded, totalBytes, speed)
                    lastUpdateMs = now
                    lastBytes = currentDownloaded
                }
            }
        }

        if (tmpFile.renameTo(outputFile)) {
            _info.value = _info.value.copy(
                status = DownloadStatus.COMPLETED, filePath = outputFile.absolutePath,
                downloadedBytes = totalBytes,
            )
            listener?.onComplete(outputFile)
        }
    }

    private suspend fun downloadChunked() = withContext(Dispatchers.IO) {
        val totalBytes = getFileSize()
        _info.value = _info.value.copy(totalBytes = totalBytes)

        val chunkSize = totalBytes / config.chunkCount
        val progress = AtomicLong(0)
        var lastUpdateMs = System.currentTimeMillis()

        val deferreds = List(config.chunkCount) { i ->
            val start = i * chunkSize
            val end = if (i == config.chunkCount - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
            async {
                downloadChunk(i, start, end, progress, lastUpdateMs)
            }
        }
        deferreds.awaitAll()

        val outputFile = File(config.outputDir, fileName)
        outputFile.outputStream().use { out ->
            for (i in 0 until config.chunkCount) {
                val part = File(config.outputDir, "$fileName.part.$i")
                part.inputStream().use { it.copyTo(out) }
                part.delete()
            }
        }

        _info.value = _info.value.copy(
            status = DownloadStatus.COMPLETED, filePath = outputFile.absolutePath,
            downloadedBytes = totalBytes,
        )
        listener?.onComplete(outputFile)
    }

    private suspend fun getFileSize(): Long = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = config.connectTimeout
        conn.readTimeout = config.readTimeout
        conn.requestMethod = "HEAD"
        val len = conn.contentLengthLong
        if (len <= 0) throw IOException("无法获取文件大小")
        conn.disconnect()
        len
    }

    private suspend fun downloadChunk(
        index: Int, start: Long, end: Long,
        globalProgress: AtomicLong,
        lastUpdateMs: Long,
    ) = withContext(Dispatchers.IO) {
        val partFile = File(config.outputDir, "$fileName.part.$index")
        val downloaded = if (partFile.exists()) partFile.length() else 0L
        val rangeStart = start + downloaded
        if (rangeStart > end) {
            globalProgress.addAndGet(end - start + 1)
            return@withContext
        }

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = config.connectTimeout
        conn.readTimeout = config.readTimeout
        conn.setRequestProperty("Range", "bytes=$rangeStart-$end")

        if (conn.responseCode != HttpURLConnection.HTTP_PARTIAL) {
            throw IOException("分片 $index 服务器未返回206: ${conn.responseCode}")
        }

        var localLastUpdate = lastUpdateMs
        RandomAccessFile(partFile, "rw").use { raf ->
            raf.seek(downloaded)
            val input = conn.inputStream
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (paused || !isActive) return@withContext
                raf.write(buffer, 0, bytesRead)
                val current = globalProgress.addAndGet(bytesRead.toLong())
                val now = System.currentTimeMillis()
                if (now - localLastUpdate > 300) {
                    localLastUpdate = now
                    _info.value = _info.value.copy(downloadedBytes = current)
                    listener?.onProgress(current, _info.value.totalBytes, _info.value.speed)
                }
            }
        }
        conn.disconnect()
    }
}
