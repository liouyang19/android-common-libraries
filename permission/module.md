# Permission Module

基于 `accompanist-permissions` 封装的通用 Android 权限请求库。

---

## 模块结构

```
permission/
├── build.gradle.kts                              # 依赖 accompanist-permissions
└── src/main/kotlin/com/taisau/android/common/permission/
    ├── Permission.kt                             # 权限类型定义
    └── PermissionManager.kt                      # Composable 权限请求封装
```

---

## 权限类型 (`Permission`)

每种权限对应一个 `object`，自动处理 SDK 版本差异：

| 权限 | 使用方式 | SDK 适配 |
|------|---------|---------|
| 相机 | `Permission.Camera` | 所有版本 |
| 通知 | `Permission.Notifications` | < 33 时自动跳过（无需申请） |
| 精确定位 | `Permission.LocationFine` | 所有版本 |
| 粗略定位 | `Permission.LocationCoarse` | 所有版本 |
| 定位（精细+粗略） | `Permission.Location` | 同时请求两种定位精度 |
| 录音 | `Permission.RecordAudio` | 所有版本 |
| 媒体-图片 | `Permission.MediaImages` | Android 13+ 专用 |
| 媒体-视频 | `Permission.MediaVideo` | Android 13+ 专用 |
| 媒体-音频 | `Permission.MediaAudio` | Android 13+ 专用 |
| 存储 | `Permission.Storage` | < 33 请求 `READ_EXTERNAL_STORAGE`，≥ 33 请求三个 `READ_MEDIA_*` |
| 外部写入 | `Permission.WriteExternalStorage` | 所有版本 |

---

## API

```kotlin
@Composable
fun rememberPermission(permission: Permission): PermissionHandle
```

### PermissionHandle

| 成员 | 类型 | 说明 |
|------|------|------|
| `isGranted` | `Boolean` | 权限是否已授予 |
| `shouldShowRationale` | `Boolean` | 是否需要展示权限说明（用户之前拒绝过） |
| `launchRequest()` | `Unit` | 发起权限请求 |

---

## 使用示例

### 基础使用

```kotlin
@Composable
fun CameraScreen() {
    val camera = rememberPermission(Permission.Camera)

    if (camera.isGranted) {
        CameraPreview()
    } else {
        Button(onClick = { camera.launchRequest() }) {
            Text("开启相机权限")
        }
    }
}
```

### 使用 LaunchedEffect 自动请求

```kotlin
@Composable
fun StorageScreen() {
    val storage = rememberPermission(Permission.Storage)

    LaunchedEffect(Unit) {
        if (!storage.isGranted) storage.launchRequest()
    }

    if (storage.isGranted) {
        FileList()
    }
}
```

### 处理权限被拒绝

```kotlin
@Composable
fun NotificationScreen() {
    val notification = rememberPermission(Permission.Notifications)
    var showRationale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!notification.isGranted) {
            if (notification.shouldShowRationale) {
                showRationale = true
            } else {
                notification.launchRequest()
            }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("需要通知权限") },
            text = { Text("开启通知权限以接收重要消息") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    notification.launchRequest()
                }) { Text("授权") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("取消") }
            }
        )
    }

    if (notification.isGranted) {
        NotificationList()
    }
}
```

---

## 注意事项

- `accompanist-permissions` API 标记为 `@ExperimentalPermissionsApi`，模块内部已通过 `@OptIn` 处理
- `Permission.Notifications` 在 Android 12 及以下自动视为已授权（无需申请）
- `Permission.Storage` 在 Android 13+ 自动切换到三个 `READ_MEDIA_*` 粒度权限
- 应用的 `AndroidManifest.xml` 仍需声明需要使用的权限
