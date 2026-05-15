# BOM 集成指南

Bill of Materials — 统一管理所有模块的版本号，消费方只需声明 BOM 即可对齐版本。

---

## 仓库配置

在项目根 `settings.gradle.kts` 或 `build.gradle.kts` 中添加 JitPack 仓库：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { setUrl("https://jitpack.io") }
        google()
        mavenCentral()
    }
}
```

---

## 依赖声明

### 方式一：通过 BOM（推荐）

使用 BOM 统管版本，各模块无需指定 `version`：

```kotlin
dependencies {
    // BOM
    implementation(platform("com.github.liouyang19.android-common-libraries:android-common-libraries-bom:1.2.2"))

    // 各模块 — 版本由 BOM 自动对齐
    implementation("com.github.liouyang19.android-common-libraries:theme")
    implementation("com.github.liouyang19.android-common-libraries:camera")
    implementation("com.github.liouyang19.android-common-libraries:permission")
    implementation("com.github.liouyang19.android-common-libraries:upgrade")
    implementation("com.github.liouyang19.android-common-libraries:download")
    implementation("com.github.liouyang19.android-common-libraries:network")
}
```

### 方式二：单独引用（不通过 BOM）

```kotlin
dependencies {
    implementation("com.github.liouyang19.android-common-libraries:camera:1.2.2")
    implementation("com.github.liouyang19.android-common-libraries:theme:1.2.2")
    implementation("com.github.liouyang19.android-common-libraries:permission:1.2.2")
    implementation("com.github.liouyang19.android-common-libraries:upgrade:1.2.2")
    implementation("com.github.liouyang19.android-common-libraries:download:1.2.2")
}
```

---

## 模块清单

| 模块 | 说明 | 最简依赖 |
|------|------|---------|
| `:camera` | Camera1 + Camera2 相机抽象，StateFlow 架构 | `com.github.liouyang19.android-common-libraries:camera` |
| `:theme` | Compose Material3 主题色、字体、渐变 | `com.github.liouyang19.android-common-libraries:theme` |
| `:permission` | Accompanist 权限请求封装，处理 Android 13+ | `com.github.liouyang19.android-common-libraries:permission` |
| `:upgrade` | App 更新库，检测/下载/安装 APK（Builder 模式） | `com.github.liouyang19.android-common-libraries:upgrade` |
| `:download` | 文件下载库，支持断点续传 + 分片下载 | `com.github.liouyang19.android-common-libraries:download` |
| `:network` | 网络连接状态监测（WiFi/移动/以太网） | `com.github.liouyang19.android-common-libraries:network` |

---

## 各模块使用入口

### camera

```kotlin
// 获取默认后置相机
val cameraProvider = CameraProviderImpl(context)
cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA)
```

### theme

```kotlin
// 在 Compose 中使用
MaterialTheme(
    colors = CommonLightColors,
    typography = CommonTypography,
) {
    AppContent()
}
```

### permission

```kotlin
// Compose 权限请求
val camera = rememberPermission(Permission.Camera)
Button(onClick = { camera.launchRequest() }) {
    Text(if (camera.isGranted) "已授权" else "授权相机")
}
```

### upgrade

```kotlin
// Builder 模式检测并下载更新
val manager = UpgradeManager.Builder(context)
    .setCheckUrl("https://api.example.com/check")
    .build()

lifecycleScope.launch {
    manager.check().onSuccess { info ->
        if (info.hasNewVersion) {
            manager.download(info)
        }
    }
}
```

### download

```kotlin
// Builder 模式文件下载，支持断点续传与分片
val manager = DownloadManager.Builder()
    .setOutputDir(context.cacheDir)
    .setChunkCount(4)
    .build()

val task = manager.download(lifecycleScope, url, "bigfile.zip") {
    downloaded, total, speed -> /* 更新进度 */
}
```

### network

```kotlin
// 单例监听网络状态变化
val monitor = NetworkMonitor.getInstance(context)
lifecycleScope.launch {
    monitor.info.collect { info ->
        when (info.type) {
            ConnectionType.WIFI -> /* WiFi 环境 */
            ConnectionType.MOBILE -> /* 移动网络，注意流量 */
            ConnectionType.NONE -> /* 无网络连接 */
        }
    }
}
```

---

## 版本号规则

- 版本号通过 Git tag 自动生成：`git describe --tags --abbrev=0`
- 格式：`x.y.z`（语义化版本）
- 最新版本：**1.2.2**

---

## 本地构建（验证）

```bash
./gradlew build
./gradlew publishToMavenLocal
```
