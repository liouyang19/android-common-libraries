# Network Module

网络连接状态监测库，基于 `ConnectivityManager.NetworkCallback` + Kotlin Flow 实现实时监听。

---

## 模块结构

```
network/
├── build.gradle.kts
└── src/main/kotlin/com/taisau/android/common/network/
    ├── NetworkInfo.kt         # 网络状态数据模型
    └── NetworkMonitor.kt      # 网络监听器（StateFlow + callbackFlow）
```

---

## 核心 API

### NetworkMonitor

| 成员 | 说明 |
|------|------|
| `info: StateFlow<NetworkInfo>` | 当前网络状态（push 模式） |
| `observe: Flow<NetworkInfo>` | 冷流观察（callbackFlow 实现） |
| `start()` | 注册 `NetworkCallback` 开始监听 |
| `stop()` | 注销回调停止监听 |
| `refresh()` | 手动刷新当前网络状态 |
| `getInstance(context)` | 全局单例（自动 start） |

### NetworkInfo

| 字段 | 类型 | 说明 |
|------|------|------|
| `isConnected` | `Boolean` | 是否有可用网络 |
| `type` | `ConnectionType` | NONE / WIFI / MOBILE / ETHERNET / BLUETOOTH / VPN / OTHER |
| `isMetered` | `Boolean` | 是否计费网络（移动数据为 true，WiFi 通常为 false） |

---

## 使用示例

### 方式一：StateFlow 持续观察

```kotlin
val monitor = NetworkMonitor(context)
monitor.start()

lifecycleScope.launch {
    monitor.info.collect { info ->
        when {
            !info.isConnected -> showNoNetwork()
            info.type == ConnectionType.MOBILE -> showCellularWarning()
            else -> showNormal()
        }
    }
}

// 页面销毁时停止监听
override fun onDestroy() {
    monitor.stop()
    super.onDestroy()
}
```

### 方式二：冷流 Flow（适合一次性观察）

```kotlin
lifecycleScope.launch {
    monitor.observe.collect { info ->
        updateNetworkStatus(info)
    }
}
// observe 使用 callbackFlow，collect 结束时自动注销回调
```

### 方式三：单例（全局共享）

```kotlin
// 全局只需初始化一次（Application 中调用）
val monitor = NetworkMonitor.getInstance(this)

// 任意位置获取最新状态
lifecycleScope.launch {
    NetworkMonitor.getInstance(this@MainActivity).info.collect { info ->
        if (!info.isConnected) showToast("网络不可用")
    }
}
```

### 手动刷新

```kotlin
// 在特定时机主动检查
buttonRefresh.setOnClickListener {
    val info = NetworkMonitor.getInstance(this).also { it.refresh() }.info.value
    textView.text = "网络类型：${info.type}，${if (info.isConnected) "已连接" else "未连接"}"
}
```
