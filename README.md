# android-common-libraries

Android 通用库集合 — 多模块 Kotlin 项目，即开即用，降低 App 开发中的重复工作。

---

## 模块清单

| 模块 | 状态 | 说明 | 技术栈 |
|------|------|------|--------|
| `:camera` | ✅ 稳定 | Camera1 + Camera2 相机抽象 | Coroutine + StateFlow |
| `:theme` | ✅ 稳定 | Compose Material3 主题色、字体、渐变 | Compose Material3 |
| `:permission` | ✅ 新增 | Android 权限请求封装，处理 Android 13+ | Accompanist + Compose |
| `:upgrade` | ✅ 新增 | App 更新库（检测、下载、安装） | Builder + DownloadManager |
| `:download` | ✅ 新增 | 文件下载库（断点续传、分片下载） | Builder + Coroutine |
| `:network` | ✅ 新增 | 网络连接状态监测（WiFi/移动/以太网） | Coroutine + Flow |
| `:bom` | ✅ 新增 | Bill of Materials 版本管理 | java-platform |

---

## 集成

### 仓库

在 `settings.gradle.kts` 中添加 JitPack：

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { setUrl("https://jitpack.io") }
        google()
        mavenCentral()
    }
}
```

### 依赖

**通过 BOM（推荐）— 版本自动对齐：**

```kotlin
dependencies {
    implementation(platform("com.github.liouyang19.android-common-libraries:android-common-libraries-bom:1.2.2"))
    implementation("com.github.liouyang19.android-common-libraries:camera")
    implementation("com.github.liouyang19.android-common-libraries:theme")
    implementation("com.github.liouyang19.android-common-libraries:permission")
    implementation("com.github.liouyang19.android-common-libraries:upgrade")
    implementation("com.github.liouyang19.android-common-libraries:download")
    implementation("com.github.liouyang19.android-common-libraries:network")
}
```

**单独引用：**

```kotlin
implementation("com.github.liouyang19.android-common-libraries:camera:1.2.2")
implementation("com.github.liouyang19.android-common-libraries:theme:1.2.2")
implementation("com.github.liouyang19.android-common-libraries:permission:1.2.2")
implementation("com.github.liouyang19.android-common-libraries:upgrade:1.2.2")
implementation("com.github.liouyang19.android-common-libraries:download:1.2.2")
implementation("com.github.liouyang19.android-common-libraries:network:1.2.2")
```

---

## 快速开始

### camera — 相机封装

```kotlin
val cameraProvider = CameraProviderImpl(context)
cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA)
```

### theme — Compose 主题

```kotlin
MaterialTheme(colors = CommonLightColors, typography = CommonTypography) {
    AppContent()
}
```

### permission — 权限请求

```kotlin
val camera = rememberPermission(Permission.Camera)
if (!camera.isGranted) {
    Button(onClick = { camera.launchRequest() }) { Text("授权相机") }
}
```

### upgrade — App 更新

```kotlin
val manager = UpgradeManager.Builder(context).setCheckUrl("https://...").build()
lifecycleScope.launch {
    manager.check().onSuccess { info ->
        if (info.hasNewVersion) manager.download(info)
    }
}
```

### download — 文件下载

```kotlin
val manager = DownloadManager.Builder().setOutputDir(context.cacheDir).setChunkCount(4).build()
val task = manager.download(lifecycleScope, url, "bigfile.zip") { downloaded, total, speed ->
    // 更新进度
}
```

### network — 网络监测

```kotlin
val monitor = NetworkMonitor.getInstance(this)
lifecycleScope.launch {
    monitor.info.collect { info ->
        if (!info.isConnected) showToast("网络不可用")
    }
}
```

---

## 本地构建

```powershell
# 完整构建
./gradlew build

# 发布到本地 Maven
./gradlew publishToMavenLocal
```

### 环境要求

- Android Studio Hedgehog+
- JDK 21
- Gradle 9.4.1（wrapper 自带）
- Min SDK 26

---

## 项目结构

```
android-common-libraries/
├── build.gradle.kts              # 根构建 — 集中管理发布
├── settings.gradle.kts           # 模块注册
├── gradle.properties             # 版本号、GroupId
├── jitpack.yml                   # JitPack CI 配置
├── gradle/
│   └── git-tag-version.gradle.kts  # Git tag 版本自动提取
├── camera/          → com.taisau.android.common.camera
├── theme/           → com.taisau.android.common.theme
├── permission/      → com.taisau.android.common.permission
├── upgrade/         → com.taisau.android.common.upgrade
├── download/        → com.taisau.android.common.download
├── network/         → com.taisau.android.common.network
├── bom/             → Bill of Materials
└── new-module.ps1   # 一键创建新模块
```

---

## 发布流程

```powershell
git add -A
git commit -m "发布x.y.z: ..."
git tag -a x.y.z -m "发布x.y.z"
git push origin main --tags
# JitPack 自动构建：https://jitpack.io/#liouyang19/android-common-libraries/x.y.z
```

---

## 许可证

Apache 2.0
