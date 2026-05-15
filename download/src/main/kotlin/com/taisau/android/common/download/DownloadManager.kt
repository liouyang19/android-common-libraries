package com.taisau.android.common.download

import kotlinx.coroutines.CoroutineScope
import java.io.File

class DownloadManager private constructor(
    val config: Config,
) {
    data class Config(
        val outputDir: File,
        val chunkCount: Int = 1,
        val connectTimeout: Int = 15_000,
        val readTimeout: Int = 15_000,
    )

    class Builder {
        private var outputDir: File = File("")
        private var chunkCount: Int = 1
        private var connectTimeout: Int = 15_000
        private var readTimeout: Int = 15_000

        fun setOutputDir(dir: File) = apply { outputDir = dir }
        fun setChunkCount(count: Int) = apply {
            chunkCount = count.coerceAtLeast(1)
        }
        fun setConnectTimeout(timeout: Int) = apply { connectTimeout = timeout }
        fun setReadTimeout(timeout: Int) = apply { readTimeout = timeout }

        fun build() = DownloadManager(
            Config(outputDir, chunkCount, connectTimeout, readTimeout),
        )
    }

    fun download(
        scope: CoroutineScope,
        url: String,
        fileName: String,
        listener: DownloadListener? = null,
    ): DownloadTask {
        config.outputDir.mkdirs()
        val task = DownloadTask(config, url, fileName, listener)
        task.start(scope)
        return task
    }
}
