# Download Module

通用文件下载库，支持 **断点续传** 和 **分片下载**。采用 Builder 模式构建。

---

## 模块结构

```
download/
├── build.gradle.kts
└── src/main/kotlin/com/taisau/android/common/download/
    ├── DownloadInfo.kt         # 数据模型
    ├── DownloadListener.kt     # 回调接口
    ├── DownloadManager.kt      # 入口（Builder 模式）
    └── DownloadTask.kt         # 下载任务（断点续传 + 分片）
```

---

## 核心 API

### DownloadManager — Builder

```kotlin
val manager = DownloadManager.Builder()
    .setOutputDir(filesDir)          // 下载文件输出目录
    .setChunkCount(4)                // 分片数，>1 启用分片下载
    .setConnectTimeout(15_000)
    .setReadTimeout(15_000)
    .build()
```

### DownloadTask — 控制

| 方法 | 说明 |
|------|------|
| `info` | `StateFlow<DownloadInfo>`，监听下载状态 |
| `pause()` | 暂停下载 |
| `cancel()` | 取消下载并清理临时文件 |

### DownloadInfo

| 字段 | 类型 | 说明 |
|------|------|------|
| `url` | `String` | 下载地址 |
| `fileName` | `String` | 文件名 |
| `totalBytes` | `Long` | 文件总大小 |
| `downloadedBytes` | `Long` | 已下载大小 |
| `status` | `DownloadStatus` | QUEUED / DOWNLOADING / PAUSED / COMPLETED / FAILED |
| `speed` | `Long` | 当前下载速度 (bytes/s) |

### DownloadListener

```kotlin
interface DownloadListener {
    fun onProgress(downloadedBytes: Long, totalBytes: Long, speed: Long)
    fun onComplete(file: File)
    fun onPause()
    fun onResume()
    fun onError(e: Exception)
}
```

---

## 断点续传

- 下载过程中产生 `.tmp` 临时文件（单线程）或 `.part.N` 分片文件（多分片）
- 重新启动下载时自动检测已有文件大小，通过 `Range` 请求头从断点处继续
- 下载完成后 `.tmp` / `.part.N` 文件自动清理

## 分片下载

- `setChunkCount(1)` 为单线程普通下载
- `setChunkCount(N)`（N>1）启用 N 个并发分片，每个分片通过 `Range` 请求头下载独立区间
- 所有分片下载完成后合并为最终文件

---

## 使用示例

### 简单下载

```kotlin
val manager = DownloadManager.Builder()
    .setOutputDir(context.filesDir)
    .build()

val task = manager.download(
    scope = lifecycleScope,
    url = "https://example.com/file.apk",
    fileName = "app.apk",
    listener = object : DownloadListener {
        override fun onProgress(downloaded: Long, total: Long, speed: Long) {
            // update progress bar
        }
        override fun onComplete(file: File) {
            // handle file
        }
    },
)

// 观察 StateFlow
lifecycleScope.launch {
    task.info.collect { info ->
        when (info.status) {
            DownloadStatus.COMPLETED -> showSuccess(info.filePath)
            DownloadStatus.FAILED -> showError()
            else -> showProgress(info.downloadedBytes, info.totalBytes)
        }
    }
}
```

### 分片下载 + 断点续传

```kotlin
val manager = DownloadManager.Builder()
    .setOutputDir(context.cacheDir)
    .setChunkCount(4)
    .build()

val task = manager.download(lifecycleScope, url = bigFileUrl, fileName = "bigfile.zip")

// 暂停
buttonPause.setOnClickListener { task.pause() }

// 恢复 — 自动从已下载部分继续
buttonResume.setOnClickListener {
    manager.download(lifecycleScope, task.url, task.fileName, ...)
}
```

### 暂停后恢复

```kotlin
// pause 只是取消协程，临时文件保留
task.pause()

// 重新 start 自动检测临时文件长度，通过 Range 请求头续传
val resumedTask = manager.download(lifecycleScope, url, fileName, listener)
// resumedTask.info.collect 会看到 downloadedBytes 从断点开始增长
```
