# Upgrade Module

App 更新库，采用 **Builder 模式** 构建，支持检测新版本、下载 APK、安装更新。

---

## 模块结构

```
upgrade/
├── build.gradle.kts
└── src/main/kotlin/com/taisau/android/common/upgrade/
    ├── UpgradeInfo.kt         # 版本信息数据模型
    └── UpgradeManager.kt      # 更新管理器（Builder 模式）
```

---

## 核心 API

### UpgradeManager

使用 Builder 模式构造：

```kotlin
val manager = UpgradeManager.Builder(context)
    .setCheckUrl("https://api.example.com/check")
    .addHeader("Authorization", "token")
    .setConnectTimeout(10_000)
    .setReadTimeout(10_000)
    .setAuthority("${context.packageName}.fileprovider")
    .build()
```

### UpgradeInfo

服务端 JSON 响应格式：

```json
{
    "hasNewVersion": true,
    "versionCode": 2,
    "versionName": "1.0.1",
    "downloadUrl": "https://example.com/app.apk",
    "changeLog": "Bug fixes",
    "forceUpdate": false,
    "fileMd5": "abc123",
    "fileSize": 5242880
}
```

### DownloadProgress

| 属性 | 类型 | 说明 |
|------|------|------|
| `bytesDownloaded` | `Long` | 已下载字节 |
| `totalBytes` | `Long` | 总字节数 |
| `progress` | `Float` | 下载进度 (0..1) |
| `isSuccessful` | `Boolean` | 下载是否成功 |
| `isFailed` | `Boolean` | 下载是否失败 |
| `isRunning` | `Boolean` | 下载中 |
| `isPending` | `Boolean` | 等待中 |

---

## 使用示例

### 检测新版本

```kotlin
lifecycleScope.launch {
    when (val result = manager.check()) {
        is Result.Success -> {
            val info = result.getOrThrow()
            if (info.hasNewVersion) {
                showUpdateDialog(info)
            }
        }
        is Result.Failure -> {
            handleError(result.exceptionOrNull())
        }
    }
}
```

### 下载 APK

```kotlin
// 开始下载
val downloadId = manager.download(info)

// 轮询进度（例如在 LaunchedEffect 中）
LaunchedEffect(downloadId) {
    while (isActive) {
        val progress = manager.getProgress(downloadId)
        updateProgress(progress.progress)
        if (progress.isSuccessful || progress.isFailed) break
        delay(1000)
    }
}
```

### 安装 APK

```kotlin
manager.install(downloadId)
```

### 完整流程

```kotlin
lifecycleScope.launch {
    when (val result = manager.check()) {
        is Result.Success -> {
            val info = result.getOrThrow()
            if (info.hasNewVersion && userConfirmed) {
                val downloadId = manager.download(info)
                // 等待下载完成
                if (manager.getProgress(downloadId).isSuccessful) {
                    manager.install(downloadId)
                }
            }
        }
        is Result.Failure -> { /* 处理错误 */ }
    }
}
```

---

## 消费者配置要求

### 1. 添加 FileProvider

在 `AndroidManifest.xml` 中：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### 2. 创建 `res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="downloads" path="Download/" />
</paths>
```

### 3. 申请权限

Android 13+ 需要 `POST_NOTIFICATIONS` 权限才能显示下载通知。需要时请使用 `permission` 模块请求：

```kotlin
val notification = rememberPermission(Permission.Notifications)
```
