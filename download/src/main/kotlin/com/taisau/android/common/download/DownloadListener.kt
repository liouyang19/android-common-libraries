package com.taisau.android.common.download

import java.io.File

interface DownloadListener {
    fun onProgress(downloadedBytes: Long, totalBytes: Long, speed: Long) = Unit
    fun onComplete(file: File) = Unit
    fun onPause() = Unit
    fun onResume() = Unit
    fun onError(e: Exception) = Unit
}
