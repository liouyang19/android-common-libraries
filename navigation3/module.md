# navigation3

基于 Jetpack Compose Navigation3（`androidx.navigation3`）的轻量封装，提供多 Tab 拓扑结构的导航能力。

## 依赖

```kotlin
// settings.gradle.kts
include(":navigation3")

// build.gradle.kts
implementation(project(":navigation3"))
```

## 结构

```
navigation3/
├── build.gradle.kts                # taisau.android.library.compose + kotlin.serialization
├── src/main/kotlin/.../navigation3/
│   ├── NavigationState.kt          # 导航状态 + 组合入口
│   └── Navigator.kt                # 导航动作
```

## 核心概念

### 两层级联

- **Top-level (Tab 层)** — 用 `NavBackStack<NavKey>` 记录用户切换到哪个顶层页面，支持前进/回退
- **Sub-stack (子层)** — 每个 top-level key 对应一个子栈，用于该 Tab 下的页面跳转

### NavigationState

通过 `rememberNavigationState(startKey, topLevelKeys)` 创建：

| 属性 | 说明 |
|------|------|
| `topLevelStack` | 顶层栈，记录 Tab 切换历史 |
| `subStacks` | 每个 top-level 对应的子栈 |
| `currentTopLevelKey` | 当前激活的 Tab Key |
| `currentKey` | 当前页面 key（子栈栈顶） |

### Navigator

`Navigator(state)` — 根据目标 key 的位置自动路由：

| 方法 | 导航策略 |
|------|----------|
| `navigate(key)` | 等于 `currentTopLevelKey` → 清子栈；在 `topLevelKeys` 中 → 切换到目标 Tab；否则 → 在当前子栈中压入 |
| `goBack()` | 当前在子栈 → 回退子栈；当前在顶层 → 回退顶层栈；已在 startKey → 抛异常 |

### 渲染入口

```kotlin
@Composable
fun NavigationState.toEntries(entryProvider: (NavKey) -> NavEntry<NavKey>)
```

将所有栈合并为一个平坦 `SnapshotStateList<NavEntry<NavKey>>`，供 `NavHost` 消费。已自动添加 `SaveableStateHolder` 和 `ViewModelStore` 装饰器。

## 使用示例

```kotlin
val startKey = NavKey("home")
val topLevelKeys = setOf(
    NavKey("home"),
    NavKey("discover"),
    NavKey("profile"),
)

val state = rememberNavigationState(startKey, topLevelKeys)
val navigator = remember { Navigator(state) }
val entries = state.toEntries { key -> /* 返回对应 NavEntry */ }

// 导航
navigator.navigate(NavKey("detail/123"))  // 跳子页
navigator.navigate(NavKey("discover"))     // 切换 Tab
navigator.goBack()                          // 回退
```
