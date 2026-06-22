# android-common-libraries

Android common libraries — a multi-module Kotlin project (Apache 2.0).

## Build & test

```powershell
# Full build
./gradlew build

# Build only active modules
./gradlew :camera:build :theme:build

# Run all tests
./gradlew test

# Run tests for a single module
./gradlew :camera:test
```

Build commands require `local.properties` with a valid SDK path.

## Monorepo structure

| Module | Status | Purpose |
|--------|--------|---------|
| `:camera` | Active | Camera abstraction (Camera1 + Camera2 APIs). Published as Maven artifact (`com.github.liouyang19.android-common-libraries:camera:0.1.0`). |
| `:theme` | Active | Compose Material3 theme (colors, typography, gradients). |
| `:app` | Disabled | Sample app — build config fully commented out, no Kotlin sources. |
| `:navigation3` | Disabled | All build config commented out, no source files. |
| `:permission` | Disabled | All build config commented out, no source files. |
| `:upgrade` | Disabled | All build config commented out, no source files. |

## Architecture

- **Namespace:** `com.taisau.android.common.*`
- **GroupId:** `com.github.liouyang19.android-common-libraries`, version `0.1.0`
- **Gradle 9.4.1** — version catalog pulled from external source `com.github.liouyang19.android-gradle-plugins:version-catalog:1.3.7` (no local `libs.versions.toml`)
- Custom plugins (`taisau.android.library`, `taisau.android.library.compose`, `taisau.dokka`) come from the same external plugin group
- **Camera module** uses coroutine + StateFlow architecture with `CameraBridge` (mode switching) and `CameraProviderImpl` (lifecycle binding). Supports Camera1 and Camera2 backends, auto-detected at init.
- **Theme module** depends on `androidx.compose.material3`
- **Min SDK 26, Java 11** target

## Conventions

- Comments in source code are in Chinese
- No `mavenLocal()` is configured for CI — build triggers: `./gradlew build publishToMavenLocal`
- Only `:camera` and `:theme` build successfully without modification (other modules have commented-out build config)
- No CI, no pre-commit hooks, no lint/formatting config in this repo

## New module scaffolding

`new-module.ps1 -Name <name> [-Type compose] [-Publish]`

Creates a module with the correct custom plugin (`taisau.android.library` or `taisau.android.library.compose`), registers it in `settings.gradle.kts`, and optionally adds maven-publish config matching the `:camera` pattern.

---

## `:download` module state

### Goal
Pluggable Android download library with OkHttp engine, Room persistence, Flow-based observation, chunked download, and singleton lifecycle management.

### Build
```
./gradlew :download:compileDebugKotlin   # Zero warnings
```

Plugins: `taisau.android.library` + `kotlin.ksp` + `maven-publish`
Dependencies: OkHttp 5.3.2, okhttp-coroutines, Okio, Room runtime+ktx 2.8.4, Room compiler (KSP), kotlinx-coroutines

### Public API
- `DownloadManager` interface — `download()`, `pause()`, `cancel()`, `observeDownloadById()`, `getAllRecords()`, `getAllIncompleteRecords()`, `destroy()`
- `Context.downloadManager` — 扩展属性，获取/创建与 Application 绑定的单例 (模仿 Coil 模式)
- `SingletonDownloadManager.Factory` — Application 实现此接口可自定义单例创建
- `DownloadManager.Builder` — `setEngine()`, `setDownloadDao()`, `setOutputDir()`, `setMaxConcurrentDownloads()`, etc.
- `DownloadConfig` — top-level config data class
- `DownloadModel` / `DownloadStatus` / `DownloadInfo` — state and UI models

### Default implementations
| Role | Class | Notes |
|------|-------|-------|
| HTTP engine | `OkHttpEngine` | Connection pooling, TLS, auto-redirect; wraps `Response` in `InputStream` that auto-closes response |
| DAO | `RoomDownloadDao` | Room 2.8.4, KSP annotation processing, `fallbackToDestructiveMigration(false)` |
| Entity | `DownloadEntity` | `@Entity(tableName = "download_entities")`, `@PrimaryKey(autoGenerate = true)`, 17 fields |

### Architecture
- `DownloadManagerImpl` — single `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, `Semaphore` for concurrency limit
- `DownloadTask` — single-file suspend download with Range/206 resume, progress callbacks
- `ChunkedDownloader` — HEAD for file size → N parallel `DownloadTask` with Range → merge → cleanup `.part.*`
- `DownloadDao` interface → `RoomDownloadDao` (`RoomDownloadDao` → internal `DownloadEntityDao` @Dao → `DownloadRoomDatabase`)
- State emission: `ConcurrentHashMap<Int, MutableStateFlow<DownloadModel?>>` per download ID
- Persist debounce: `ConcurrentHashMap<Int, Long>` 1-second throttle
- **Singleton (Coil 模式):** `Context.downloadManager` → `SingletonDownloadManager.get(context)` (内部 `AtomicReference<Any?>`, 无锁) → 如果通过 `setSafe` / Application 实现了 `SingletonDownloadManager.Factory` 则调用，否则使用 `Builder` 默认配置

### Recent changes
- Switched default HTTP engine from `HttpUrlConnectionEngine` → `OkHttpEngine`
- Switched default DAO from `SqliteDownloadDao` → `RoomDownloadDao`
- Removed `HttpUrlConnectionEngine.kt`, `SqliteDownloadDao.kt`, `taisau.android.room` plugin
- Added `kotlin.ksp` plugin for Room annotation processing
- Fixed `DownloadManagerImpl.download()` `mkdirs` bug
- 删除旧的 `companion object` 单例 (`initialize()` / `getInstance()` / `resetForTest()`)
- 添加 `SingletonDownloadManager.Factory` 接口 — Application 实现后可自定义单例创建
- 添加 `Context.downloadManager` 扩展属性 — 获取/创建与 Application 绑定的单例
- 添加 `SingletonDownloadManager` — 内部 `AtomicReference<Any?>` (无锁), 支持 `setSafe` / `setUnsafe` / `reset`
- 添加 `DelicateDownloadApi` 注解标记不安全的单例操作

### Related files
- `download/build.gradle.kts`
- `download/src/main/kotlin/.../download/DownloadManager.kt` — interface, Builder
- `download/src/main/kotlin/.../download/Context+DownloadManager.kt` — Context.downloadManager 扩展
- `download/src/main/kotlin/.../download/SingletonDownloadManager.kt` — 内部单例持有者 (AtomicReference)
- `download/src/main/kotlin/.../download/DelicateDownloadApi.kt` — 不安全 API 标记注解
- `download/src/main/kotlin/.../download/DownloadConfig.kt`
- `download/src/main/kotlin/.../download/DownloadStatus.kt` — do NOT modify
- `download/src/main/kotlin/.../download/DownloadInfo.kt`
- `download/src/main/kotlin/.../download/DownloadModel.kt`
- `download/src/main/kotlin/.../download/engine/DownloadEngine.kt` — interface + DownloadResponse
- `download/src/main/kotlin/.../download/engine/OkHttpEngine.kt` — default engine
- `download/src/main/kotlin/.../download/download/DownloadRequest.kt`
- `download/src/main/kotlin/.../download/download/DownloadTask.kt`
- `download/src/main/kotlin/.../download/download/ChunkedDownloader.kt`
- `download/src/main/kotlin/.../download/download/DownloadManagerImpl.kt`
- `download/src/main/kotlin/.../download/db/DownloadDao.kt`
- `download/src/main/kotlin/.../download/db/DownloadEntity.kt`
- `download/src/main/kotlin/.../download/db/RoomDownloadDao.kt`
- `download/src/main/kotlin/.../download/utils/DownloadConst.kt`
- `download/src/main/kotlin/.../download/utils/FileUtil.kt`
- `download/src/main/kotlin/.../download/utils/UserAction.kt`
