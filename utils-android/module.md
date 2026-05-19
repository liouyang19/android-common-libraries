# Utils-Android Module

A collection of commonly used Android utility classes, providing convenient wrappers around Android framework APIs.

## Module Structure

```
utils-android/src/main/kotlin/com/taisau/android/common/utils/android/
├── AppUtils.kt       # App info, install/uninstall, foreground detection
├── ScreenUtils.kt    # Screen dimensions, dp/sp/px conversion
├── KeyboardUtils.kt  # Soft keyboard show/hide/toggle
├── ToastUtils.kt     # Toast helper with cancel support
├── NetworkUtils.kt   # Network state check, type detection, connectivity Flow
├── FileUtils.kt      # File read/write/copy/move/delete
├── LogUtils.kt       # Log wrapper with debug toggle and global tag
├── SpUtils.kt        # SharedPreferences helper
├── VolumeUtils.kt    # Volume control, ringer mode, vibration, notification sound
├── DeviceUtils.kt    # Device info: brand, model, SDK, RAM, storage, battery
├── ColorUtils.kt     # ARGB/hex conversion, brightness, blend, contrast text color
└── NotificationUtils.kt # Channel creation, send/cancel notifications, big text/inbox/messaging styles
```

## Usage

### AppUtils

```kotlin
val versionName = AppUtils.getAppVersionName(context)
val versionCode = AppUtils.getAppVersionCode(context)
AppUtils.openAppSettings(context)
val isForeground = AppUtils.isAppInForeground(context)
AppUtils.installApk(context, apkFile, "${context.packageName}.fileprovider")
```

### ScreenUtils

```kotlin
val px = ScreenUtils.dp2px(context, 16f)
val dp = ScreenUtils.px2dp(context, 48f)
val width = ScreenUtils.getScreenWidth(context)
val height = ScreenUtils.getScreenHeight(context)
val statusBarHeight = ScreenUtils.getStatusBarHeight(context)
```

### KeyboardUtils

```kotlin
KeyboardUtils.showKeyboard(editText)
KeyboardUtils.hideKeyboard(editText)
KeyboardUtils.showKeyboardDelayed(editText)
```

### ToastUtils

```kotlin
ToastUtils.showShort(context, "Saved")
ToastUtils.showLong(context, R.string.saved_successfully)
ToastUtils.cancel()
```

### NetworkUtils

```kotlin
val isConnected = NetworkUtils.isConnected(context)
val type = NetworkUtils.getNetworkType(context)  // WIFI / MOBILE / NONE

lifecycleScope.launch {
    NetworkUtils.observeNetworkState(context).collect { connected ->
        // react to connectivity changes
    }
}
```

### FileUtils

```kotlin
val cacheDir = FileUtils.getCacheDir(context)
FileUtils.writeFile(file, "content")
val content = FileUtils.readFile(file)
FileUtils.copyFile(src, dest, overwrite = true)
val size = FileUtils.getFileSize(directory)
```

### LogUtils

```kotlin
LogUtils.isDebug = BuildConfig.DEBUG
LogUtils.globalTag = "MyApp"

LogUtils.d("Login", "User logged in: %s", userId)
LogUtils.e("Login", throwable)
```

### SpUtils

```kotlin
val sp = SpUtils(context, "user_prefs")
sp.putString("token", "abc123")
val token = sp.getString("token")
sp.putBoolean("dark_mode", true)
sp.remove("temp_data")
sp.clear()
```

### VolumeUtils

```kotlin
VolumeUtils.setMusicVolume(context, 10)
VolumeUtils.vibrate(context, 500L)
VolumeUtils.playNotificationSound(context)
VolumeUtils.setRingerModeSilent(context)
VolumeUtils.isSilentMode(context)
```

### DeviceUtils

```kotlin
DeviceUtils.brand              // "Xiaomi"
DeviceUtils.model              // "Mi 14"
DeviceUtils.sdkInt             // 35
DeviceUtils.isRooted()         // false
DeviceUtils.isEmulator()       // false
DeviceUtils.getBatteryPercentage(context)  // 85
DeviceUtils.isCharging(context)
DeviceUtils.getTotalRAM()      // bytes
DeviceUtils.getAvailableInternalStorage()  // bytes
```

### ColorUtils

```kotlin
ColorUtils.parseColor("#FF8800")
ColorUtils.toHex(color)              // "#ffff8800"
ColorUtils.isLight(color)            // true
ColorUtils.contrastTextColor(color)  // Color.BLACK or Color.WHITE
ColorUtils.blendColors(color1, color2, 0.5f)
ColorUtils.darken(color, 0.3f)
ColorUtils.lighten(color, 0.3f)
```

### NotificationUtils

```kotlin
NotificationUtils.createChannel(context, "chat", "Chat Messages",
    importance = NotificationManager.IMPORTANCE_HIGH)

NotificationUtils.sendNotification(context, id = 1, channelId = "chat",
    title = "New message", content = "Hello!", smallIcon = R.drawable.ic_notify)

NotificationUtils.createBigTextNotification(context, id = 2,
    title = "Alert", content = "Summary", bigText = "Long text...",
    smallIcon = R.drawable.ic_notify)

NotificationUtils.cancelNotification(context, id = 1)
NotificationUtils.cancelAllNotifications(context)
```
