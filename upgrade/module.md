# Upgrade Module

App 更新库，采用 **Builder 模式** 构建，支持检测新版本、下载 APK、安装更新。
集成了 **AndroidX WorkManager**，支持按指定条件（网络、充电、空闲等）定时检查更新。
**check / download / install 等核心方法已拆分为三个子接口**：
[CheckStrategy]、[DownloadStrategy]、[InstallStrategy]，
统一继承自 [UpgradeStrategy]，默认使用 [DefaultUpgradeStrategy]（HttpURLConnection + DownloadManager），
使用者可注入任一或全部策略的自定义实现。

---

## 模块结构

```
upgrade/
├── build.gradle.kts
└── src/main/kotlin/com/taisau/android/common/upgrade/
    ├── UpgradeInfo.kt                 # 版本信息数据模型
    ├── UpgradeState.kt                # 升级状态密封接口（Idle/Checking/Downloading/…）
    ├── UpgradeManager.kt              # 更新管理器接口 + Builder
    ├── UpgradeManagerImpl.kt          # 更新管理器实现（状态跟踪 + 进度轮询）
    ├── strategy/
    │   ├── CheckStrategy.kt           # 版本检测策略接口：check(checkUrl)
    │   ├── DownloadStrategy.kt        # 下载策略接口：download(downloadUrl, savePath)
    │   ├── InstallStrategy.kt         # 安装策略接口：install(savePath)
    │   ├── UpgradeStrategy.kt         # 总接口（继承以上三个子接口）
    │   ├── DefaultCheckStrategy.kt    # 默认检测实现（HttpURLConnection）
    │   ├── DefaultDownloadStrategy.kt # 默认下载实现（DownloadManager）
    │   ├── DefaultInstallStrategy.kt  # 默认安装实现（FileProvider + Intent.ACTION_VIEW）
    │   └── DefaultUpgradeStrategy.kt  # 组合以上三个默认策略
    └── scheduler/
        ├── UpgradeScheduleConfig.kt   # 调度配置（间隔、网络、充电约束）
        ├── CheckWorker.kt             # WorkManager 版本检测 Worker
        ├── DownloadWorker.kt          # WorkManager 下载 Worker
        ├── InstallWorker.kt           # WorkManager 安装 Worker
        ├── UpgradeScheduler.kt        # WorkManager 定时调度封装
        ├── UpdateMode.kt              # 更新模式枚举
        ├── StrategyHolder.kt          # 策略持有者（Worker 与 Manager 的桥接）
        └── WorkerUtils.kt             # Worker 工具函数
```

---

## 核心 API

### UpgradeManager（接口）

[UpgradeManager] 是接口定义，通过 [UpgradeManager.Builder] 构建，返回 [UpgradeManagerImpl] 实例。

```kotlin
val manager = UpgradeManager.Builder(context)
    .setCheckUrl("https://api.example.com/check")
    .setNotifyEnabled(true)                           // 开启通知栏通知
    .setSaveDirectory("/sdcard/Download/upgrade")      // 指定 APK 存放目录（可选）
    .build()
```

### 状态跟踪

[UpgradeManager.state] 以 [StateFlow] 形式对外暴露当前升级步骤（[UpgradeState]）：

| 状态 | 含义 |
|------|------|
| `Idle` | 空闲（初始 / 流程结束） |
| `Checking` | 正在检测新版本 |
| `NewVersionReady(info)` | 检测完成，有新版本 |
| `NoNewVersion` | 检测完成，无新版本 |
| `CheckFailed(error)` | 检测失败 |
| `Downloading(progress, bytesDownloaded, totalBytes, downloadId)` | 正在下载（含实时进度） |
| `DownloadCompleted(savePath)` | 下载完成 |
| `DownloadFailed(error)` | 下载失败 |
| `Installing` | 正在安装 |
| `Installed` | 安装完成 |
| `InstallFailed(error)` | 安装失败 |

调用方 collect 此状态即可驱动 UI 更新或自动执行后续步骤。

### Builder 配置项

| 方法 | 说明 |
|------|------|
| `setCheckUrl(url)` | （必须）设置版本检查接口 URL |
| `setNotifyEnabled(true)` | 开启通知栏通知（检测到新版本、下载完成、安装完成时显示） |
| `setSaveDirectory(path)` | 设置 APK 下载存放目录，传入 null 或空字符串时自动使用 `cacheDir/upgrade/` |
| `setStrategy(UpgradeStrategy)` | 一次性注入完整的升级策略 |
| `setCheckStrategy(CheckStrategy)` | 单独注入版本检测策略 |
| `setScheduleConfig(config)` | 配置 WorkManager 定时检查参数 |

### UpgradeManager + 统一自定义策略

```kotlin
val manager = UpgradeManager.Builder(context)
    .setCheckUrl("https://api.example.com/check")
    .setStrategy(object : UpgradeStrategy {
        override suspend fun check(checkUrl: String): Result<UpgradeInfo> = ...
        override fun download(downloadUrl: String, savePath: String): Long = ...
        override fun install(savePath: String) = ...
    })
    .build()
```

### UpgradeManager + 分别注入子策略

```kotlin
val manager = UpgradeManager.Builder(context)
    .setCheckUrl("https://api.example.com/check")
    .setCheckStrategy(MyCheckStrategy())       // 仅替换检测逻辑
    .setDownloadStrategy(MyDownloadStrategy()) // 仅替换下载逻辑
    .setInstallStrategy(MyInstallStrategy())   // 仅替换安装逻辑
    .build()
```

### UpgradeManager + WorkManager 定时调度

```kotlin
val manager = UpgradeManager.Builder(context)
    .setCheckUrl("https://api.example.com/check")
    // 配置 WorkManager 定时检查（每天一次，仅 WiFi）
    .setScheduleConfig(UpgradeScheduleConfig.wifiOnly())
    // 发现新版本时发送系统通知
    .setScheduleNotifyEnabled(true)
    .setScheduleNotifyTitle("有新版本可用")
    .build()

// 启动定时检查
manager.scheduleUpdateCheck()

// 查询是否已调度
val active = manager.isScheduledCheck()

// 取消定时检查
manager.cancelScheduledCheck()
```

### UpgradeScheduleConfig

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `intervalHours` | `Long` | `24` | 定期间隔（小时） |
| `requiredNetworkType` | `NetworkType` | `CONNECTED` | 所需网络类型 |
| `requiresCharging` | `Boolean` | `false` | 是否仅在充电时检查 |
| `requiresDeviceIdle` | `Boolean` | `false` | 是否仅在设备空闲时检查 |
| `initialDelayHours` | `Long` | `0` | 首次检查延迟（小时），设置时间段后自动对齐 |
| `requiresBatteryNotLow` | `Boolean` | `true` | 是否要求电量不低 |
| `requiresStorageNotLow` | `Boolean` | `true` | 是否要求存储不低 |
| `updateMode` | `UpdateMode` | `NOTIFY_ONLY` | 发现新版本后的更新模式 |
| `timeWindowStartHour` | `Int?` | `null` | 允许检测的时间段起始小时（0-23），null 不限制 |
| `timeWindowEndHour` | `Int?` | `null` | 允许检测的时间段结束小时（0-23），null 不限制 |

### UpdateMode

| 枚举值 | 说明 |
|--------|------|
| `NOTIFY_ONLY` | 仅发送新版本通知，由用户手动操作下载/安装（默认） |
| `CONFIRM_DOWNLOAD` | 通知附带"下载"按钮，用户确认后开始下载，下载完成自动触发安装 |
| `AUTO_DOWNLOAD` | 自动下载 APK，下载完成后自动触发安装，无需用户干预 |

预置配置：

| 静态方法 | 说明 |
|----------|------|
| `wifiOnly()` | 仅在 WiFi 下检查 |
| `wifiAndCharging()` | 仅在 WiFi + 充电时检查 |
| `idleAndWifi()` | 仅在空闲 + WiFi 时检查 |
| `anyNetwork()` | 有网络即可，不限制电量/存储 |
| `overnightWindow(2, 5)` | 仅在凌晨 2:00~5:59 时间段检测 |
| `lateNightWindow()` | 仅在深夜 23:00~次日 6:00 时间段检测 |

### 策略接口一览

| 接口 | 方法 | 说明 |
|------|------|------|
| [CheckStrategy] | `suspend check(checkUrl)` | 仅接收 checkUrl，策略内部自行处理超时/请求头 |
| [DownloadStrategy] | `download(downloadUrl, savePath)` | 仅接收下载地址和存放路径，返回 downloadId |
| [InstallStrategy] | `install(savePath)` | 仅接收 APK 文件路径 |
| [UpgradeStrategy] | 继承上述三接口 | 总接口，可一次性实现全部三个方法 |

**默认实现 [DefaultUpgradeStrategy]（由三个默认策略组合而成）：**
- `check` → [DefaultCheckStrategy]：HttpURLConnection GET 请求（内部硬编码 10s 超时），在 `Dispatchers.IO` 执行
- `download` → [DefaultDownloadStrategy]：系统 `DownloadManager`
- `install` → [DefaultInstallStrategy]：`FileProvider` + `Intent.ACTION_VIEW`

> 策略接口的参数已极致简化，**不包含 headers / timeout / authority 等配置项**。
> 这些应由策略实现类自行内部管理。
> 
> **下载进度** 通过 [UpgradeManager.state] 观察 [UpgradeState.Downloading] 中的 `progress` / `bytesDownloaded` / `totalBytes`。
> **取消下载** 调用 [UpgradeManager.cancel()] 取消当前 Worker 并清理 DownloadManager 任务。
> **check / download / install 均为无参无返回的挂起函数**，调用方仅需 collect [UpgradeManager.state] 驱动 UI。

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

### UpgradeState.Downloading（下载进度）

下载进度通过 [UpgradeManager.state] 中的 [UpgradeState.Downloading] 获取：

| 属性 | 类型 | 说明 |
|------|------|------|
| `progress` | `Float` | 下载进度 (0..1) |
| `bytesDownloaded` | `Long` | 已下载字节 |
| `totalBytes` | `Long` | 总字节数 |
| `downloadId` | `Long` | DownloadManager 下载 ID |

调用方 collect `state` 即可实时获取这些数据。不再需要手动轮询 `DownloadManager`。

---

## 使用示例

### 检测新版本（纯状态驱动）

```kotlin
lifecycleScope.launch {
    manager.check()
    // check() 不返回结果，调用方只需 collect state：
    //   Idle → Checking → NewVersionReady / NoNewVersion / CheckFailed
}

// 通过 collect state 感知结果
lifecycleScope.launch {
    manager.state.collect { state ->
        when (state) {
            is UpgradeState.NewVersionReady -> showUpdateDialog(state.info)
            is UpgradeState.NoNewVersion -> showToast("已是最新版本")
            is UpgradeState.CheckFailed -> handleError(state.error)
            else -> { /* 其他状态忽略 */ }
        }
    }
}
```

> **注意：** `check()`、`download()`、`install()` 均为无参无返回的挂起函数，
> 所有执行结果通过 [UpgradeManager.state] 观察。

### WorkManager 定时检查（每隔 12 小时，仅 WiFi + 充电）

```kotlin
val manager = UpgradeManager.Builder(context)
    .setCheckUrl("https://api.example.com/check")
    .setScheduleConfig(
        UpgradeScheduleConfig(
            intervalHours = 12,
            requiredNetworkType = NetworkType.UNMETERED,
            requiresCharging = true,
        )
    )
    .setScheduleNotifyEnabled(true)
    .build()

// 在 Application.onCreate() 中调用一次即可
manager.scheduleUpdateCheck()
```

### WorkManager 定时检查（仅在凌晨 2:00~5:00 时间段内）

```kotlin
val manager = UpgradeManager.Builder(context)
    .setCheckUrl("https://api.example.com/check")
    .setScheduleConfig(
        UpgradeScheduleConfig(
            intervalHours = 24,
            timeWindowStartHour = 2,   // 凌晨 2 点开始
            timeWindowEndHour = 5,     // 凌晨 5 点结束
        )
    )
    .setScheduleNotifyEnabled(true)
    .build()

// 首次调度时会自动对齐到凌晨 2 点
// 若 Worker 被触发但当前时间不在 2:00~5:59 内，会跳过本次检测
manager.scheduleUpdateCheck()
```

时间段行为说明：

| 场景 | startHour→endHour | 行为 |
|------|-------------------|------|
| 普通区间 | `2→5` | 仅 2:00~5:59 可检测 |
| 跨午夜 | `22→2` | 22:00~次日 1:59 可检测 |
| 全天不限制 | `null→null` | 任何时间均可检测（默认） |

### 更新模式配置示例

**用户确认后下载安装（CONFIRM_DOWNLOAD）：**

```kotlin
val manager = UpgradeManager.Builder(context)
    .setCheckUrl("https://api.example.com/check")
    .setScheduleConfig(
        UpgradeScheduleConfig(
            intervalHours = 24,
            updateMode = UpdateMode.CONFIRM_DOWNLOAD,  // 通知有新版，用户打开 App 决定
        )
    )
    .build()

// 定时检查发现新版本时发送系统通知
// 用户点击通知打开 App，通过 state 观察到 NewVersionReady 后显示下载按钮
manager.scheduleUpdateCheck()
```

**有更新直接下载安装（AUTO_DOWNLOAD）：**

```kotlin
val manager = UpgradeManager.Builder(context)
    .setCheckUrl("https://api.example.com/check")
    .setScheduleConfig(
        UpgradeScheduleConfig(
            intervalHours = 12,
            updateMode = UpdateMode.AUTO_DOWNLOAD,     // 自动下载
            requiredNetworkType = NetworkType.UNMETERED, // 仅 WiFi
        )
    )
    .build()

// 定时检查发现新版本时自动下载 APK（通过 DownloadWorker）
// 下载完成后通过 UpgradeManagerImpl 内部轮询检测进度，自动触发安装
manager.scheduleUpdateCheck()
```

### 通过 State 驱动 UI

```kotlin
// 在 Compose 中收集状态驱动 UI
val state by manager.state.collectAsState()

when (val s = state) {
    is UpgradeState.Idle -> { /* 显示"检查更新"按钮 */ }
    is UpgradeState.Checking -> { /* 显示加载中 */ }
    is UpgradeState.NewVersionReady -> {
        // 显示"下载"按钮（无需传参，状态自动携带版本信息）
        Button(onClick = { lifecycleScope.launch { manager.download() } }) {
            Text("下载 ${s.info.versionName}")
        }
    }
    is UpgradeState.NoNewVersion -> { /* 显示"已是最新" */ }
    is UpgradeState.CheckFailed -> { /* 显示错误 */ }
    is UpgradeState.Downloading -> {
        // 实时进度条
        LinearProgressIndicator(progress = s.progress)
    }
    is UpgradeState.DownloadCompleted -> {
        // 自动安装或在 UI 中显示"安装"按钮（无需传参）
        Button(onClick = { lifecycleScope.launch { manager.install() } }) {
            Text("安装")
        }
    }
    is UpgradeState.DownloadFailed -> { /* 显示错误 */ }
    is UpgradeState.Installing -> { /* 显示"安装中…" */ }
    is UpgradeState.Installed -> { /* 显示"更新完成" */ }
    is UpgradeState.InstallFailed -> { /* 显示错误 */ }
}
```

### 下载 APK（纯状态驱动）

```kotlin
// download() 无参无返回，状态自动流转：
//   NewVersionReady → Downloading → DownloadCompleted / DownloadFailed
lifecycleScope.launch {
    manager.download()
}
```

### 安装 APK（纯状态驱动）

```kotlin
// install() 无参无返回，状态自动流转：
//   DownloadCompleted → Installing → Installed / InstallFailed
lifecycleScope.launch {
    manager.install()
}
```

### 完整流程（纯状态驱动）

```kotlin
// 调用方仅需触发动作，所有结果通过 state 观察
lifecycleScope.launch {
    manager.check()
    // 观察 state:
    //   Idle → Checking → NewVersionReady / NoNewVersion / CheckFailed
    //   → (用户手动或 auto) → download() → Downloading → DownloadCompleted
    //   → (用户手动或 auto) → install() → Installing → Installed / InstallFailed
}
```

> **UpdateReceiver 已移除**，下载完成通过内部轮询 [DownloadManager] 检测，无需注册广播。
> **取消当前操作** 调用 [UpgradeManager.cancel()] 即可（同时取消 Worker、轮询和 DownloadManager 任务）。
> **check / download / install 均为无参无返回的挂起函数**，所有结果通过 [UpgradeManager.state] 观察。

### 取消当前操作

```kotlin
// 在任何时候取消当前正在执行的 check / download / install
manager.cancel()
// 状态重置为 Idle，可以重新开始
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
