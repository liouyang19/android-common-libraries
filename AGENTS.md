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
- **Gradle 9.4.1** — version catalog pulled from external source `com.github.liouyang19.android-gradle-plugins:version-catalog:1.1.7` (no local `libs.versions.toml`)
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
