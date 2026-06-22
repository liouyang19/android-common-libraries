# Camera Module

A coroutine-based camera abstraction layer supporting both Camera1 and Camera2 backends with automatic mode detection, lifecycle binding, and pluggable use cases.

## Module Structure

```
camera/src/main/kotlin/com/taisau/android/common/camera/
├── CameraBridge.kt           # Mode-switching proxy between Camera1/Camera2
├── CameraProviderImpl.kt     # Lifecycle-aware provider (main entry point)
├── core/
│   ├── CameraConfig.kt       # Central configuration (Builder pattern)
│   ├── CameraFacing.kt       # FRONT / BACK / UNKNOWN
│   ├── CameraInfo.kt         # Camera metadata: id, facing, orientation, resolutions
│   ├── CameraMode.kt         # CAMERA1 / CAMERA2 / AUTO
│   ├── CameraProvider.kt     # Provider interface (bindToLifecycle, switchCamera, etc.)
│   ├── CameraSelector.kt     # CameraX-style selector (DEFAULT_BACK_CAMERA / Builder)
│   ├── CameraState.kt        # Sealed class: Closed, Opening, Opened, Previewing, Error
│   ├── ICamera.kt            # Contract interface for backend implementations
│   ├── Resolution.kt         # Width/height with presets: SD, HD, FHD, UHD
│   └── UseCase.kt            # Abstract base for pluggable use cases
├── camera1/
│   ├── Camera1Config.kt      # Camera1-specific: white balance, scene mode, focus, jpeg quality
│   └── Camera1Impl.kt        # ICamera implementation using android.hardware.Camera
├── camera2/
│   ├── Camera2Config.kt      # Camera2-specific: hardware level, template, AF/AE modes
│   └── Camera2Impl.kt        # ICamera implementation using android.hardware.camera2
└── usecase/
    ├── PreviewUseCase.kt     # Preview on SurfaceView / TextureView / Surface
    ├── ImageCaptureUseCase.kt# Still-image capture with flash & capture mode control
    └── ImageAnalysisUseCase.kt# Frame analysis with backpressure & analysis mode
```

## Quick Start

```kotlin
// 1. Configure
val config = CameraConfig.Builder()
    .cameraMode(CameraMode.AUTO)       // AUTO picks Camera2 if available, else Camera1
    .previewResolution(Resolution.FHD)
    .captureResolution(Resolution.UHD)
    .build()

// 2. Create provider
val provider = CameraProviderImpl(context, config)
provider.initialize(context)

// 3. Create use cases
val preview = PreviewUseCase.Builder()
    .targetResolution(Resolution.FHD)
    .build()

val imageCapture = ImageCaptureUseCase.Builder()
    .captureMode(ImageCaptureUseCase.CaptureMode.MAXIMIZE_QUALITY)
    .build()

// 4. Bind to lifecycle (single camera)
lifecycleScope.launch {
    provider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        preview, imageCapture
    )
}

// 5. Capture a photo
lifecycleScope.launch {
    when (val result = imageCapture.capture(tempFile)) {
        is ImageCaptureUseCase.ImageCaptureResult.Success -> {
            // result.filePath
        }
        is ImageCaptureUseCase.ImageCaptureResult.Error -> {
            // result.exception
        }
    }
}
```

## API Reference

### CameraProvider — main entry point

| Method | Description |
|--------|-------------|
| `initialize(context)` | Initializes the camera bridge, detects available modes |
| `bindToLifecycle(lifecycle, cameraSelector, vararg useCases)` | Binds use cases to a specific camera. Multiple calls accumulate cameras (single or dual). Starts on `ON_START`, stops on `ON_STOP` |
| `switchCamera(selector)` | Switches between front/back camera while preserving current mode |
| `switchCameraMode(mode)` | Switches between Camera1/Camera2 backends |
| `updateConfig(config)` | Dynamically updates camera parameters (focus, flash, white balance, etc.) without reopening the camera |
| `getCurrentCameraMode(): StateFlow<CameraMode>` | Emits the currently active camera mode |
| `getAvailableModes(): StateFlow<List<CameraMode>>` | Emits the list of modes supported on this device |
| `getCameraState(): StateFlow<CameraState>` | Current camera state (Closed / Opening / Opened / Previewing / Error) |
| `isBound(): Boolean` | Whether the provider is bound to a lifecycle |
| `release()` | Releases all resources |

### Use Cases

#### PreviewUseCase

Displays a camera preview via `SurfaceProvider` (CameraX-style). The provider receives a `SurfaceRequest` with the target resolution and fulfills it with a `Surface`.

##### Usage — Direct (View / non-Compose)

```kotlin
val preview = PreviewUseCase.Builder()
    .targetResolution(Resolution.FHD)
    .targetAspectRatio(16f / 9f)
    .targetRotation(0)
    .build()

preview.setSurfaceProvider(SurfaceProvider { request ->
    val surface = surfaceView.holder.surface
    request.provideSurface(surface, executor) { result ->
        // handle result
    }
})
```

##### Usage — ViewModel + Compose

ViewModel 暴露 `SurfaceRequest` 为 `StateFlow`：

```kotlin
class CameraViewModel : ViewModel() {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    val preview = PreviewUseCase.Builder()
        .targetResolution(Resolution.FHD)
        .build()
        .also {
            it.setSurfaceProvider(SurfaceProvider { request ->
                _surfaceRequest.value = request
            })
        }
}
```

Compose 端收集 `StateFlow`，配合 `SurfaceView` 实现预览（双向时序安全）：

```kotlin
@Composable
fun CameraPreview(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val request by viewModel.surfaceRequest.collectAsState()
    val executor = remember { Dispatchers.Main.asExecutor() }
    val surfaceRef = remember { mutableStateOf<Surface?>(null) }

    // case 1: surface 先就绪，等 request 来
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        surfaceRef.value?.takeIf { it.isValid }?.let { surface ->
            r.provideSurface(surface, executor) { /* 处理 result */ }
        }
    }

    AndroidView(
        factory = {
            SurfaceView(LocalContext.current).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    // case 2: request 先到，等 surface 就绪
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        surfaceRef.value = holder.surface
                        request?.provideSurface(holder.surface, executor) { /* 处理 result */ }
                    }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {
                        surfaceRef.value = null
                    }
                })
            }
        },
        modifier = modifier
    )
}
```

##### SurfaceRequest API

| Method | Description |
|--------|-------------|
| `provideSurface(surface, executor, callback)` | Fulfills the request with a valid Surface |
| `willNotProvideSurface()` | Signals the provider cannot fulfill this request |
| `addRequestCancellationListener(executor, listener)` | Listens for cancellation |

#### ImageCaptureUseCase

Captures a still image to a file.

```kotlin
val imageCapture = ImageCaptureUseCase.Builder()
    .captureMode(ImageCaptureUseCase.CaptureMode.MAXIMIZE_QUALITY)
    .flashMode(ImageCaptureUseCase.FlashMode.AUTO)
    .build()

// Capture
val result = imageCapture.capture(File(cacheDir, "photo.jpg"))
```

| CaptureMode | Description |
|---|---|
| `MINIMIZE_LATENCY` | Fast capture, lower quality |
| `MAXIMIZE_QUALITY` | High quality, may be slower |
| `ZERO_SHUTTER_LAG` | Zero shutter lag (Camera2 only) |

#### ImageAnalysisUseCase

Processes camera frames via a custom analyzer.

```kotlin
val analysis = ImageAnalysisUseCase.Builder()
    .backpressureStrategy(ImageAnalysisUseCase.BackpressureStrategy.KEEP_ONLY_LATEST)
    .analysisMode(ImageAnalysisUseCase.AnalysisMode.STREAMING)
    .imageAnalyzer { image ->
        // image.bytes, image.width, image.height, image.format
    }
    .build()

// Observe results
lifecycleScope.launch {
    analysis.analysisResults.collect { result ->
        when (result) {
            is ImageAnalysisUseCase.AnalysisResult.Frame -> processFrame(result)
            is ImageAnalysisUseCase.AnalysisResult.Error -> handleError(result.exception)
        }
    }
}
```

| BackpressureStrategy | Description |
|---|---|
| `KEEP_ONLY_LATEST` | Drops old frames if analyzer is busy |
| `BLOCK_PRODUCER` | Blocks camera until analyzer finishes |
| `DROP` | Drops frame if analyzer is busy, no queue |

### CameraConfig — Builder

| Method | Default | Description |
|--------|---------|-------------|
| `cameraMode(Mode)` | `AUTO` | `CAMERA1`, `CAMERA2`, or `AUTO` |
| `previewResolution(Resolution)` | `FHD` | Target preview resolution |
| `captureResolution(Resolution)` | `UHD` | Target capture resolution |
| `analysisResolution(Resolution)` | `HD` | Target analysis resolution |
| `fps(Int)` | `30` | Target frames per second |
| `autoFocus(Boolean)` | `true` | Enable auto-focus |
| `flash(Boolean)` | `false` | Enable flash |
| `rotation(Int)` | `0` | Display rotation in degrees |
| `camera1Config {...}` | `null` | Camera1-specific overrides |
| `camera2Config {...}` | `null` | Camera2-specific overrides |

### Camera1Config — Camera1 only

| Field | Default | Description |
|-------|---------|-------------|
| `whiteBalance` | `AUTO` | AUTO, INCANDESCENT, FLUORESCENT, DAYLIGHT, CLOUDY_DAYLIGHT |
| `sceneMode` | `AUTO` | AUTO, PORTRAIT, LANDSCAPE, NIGHT, SPORTS |
| `pictureFormat` | `JPEG` | Image format for captured pictures |
| `focusMode` | `AUTO` | AUTO, MACRO, INFINITY, FIXED, CONTINUOUS_PICTURE, CONTINUOUS_VIDEO |
| `jpegQuality` | `95` | JPEG quality (0-100) |

### Camera2Config — Camera2 only

| Field | Default | Description |
|-------|---------|-------------|
| `hardwareLevel` | `FULL` | LEGACY, LIMITED, FULL, LEVEL_3 |
| `captureRequestTemplate` | `PREVIEW` | PREVIEW, STILL_CAPTURE, RECORD, ZERO_SHUTTER_LAG |
| `controlAfMode` | `null` | Override AF mode (Camera2 constant) |
| `controlAeMode` | `null` | Override AE mode (Camera2 constant) |
| `jpegQuality` | `95` | JPEG quality (0-100) |

## Camera Mode Auto-Detection

When `CameraMode.AUTO` is selected, `CameraBridge` detects available backends at initialization:

- **Camera1** is always available on all Android devices
- **Camera2** is available if `CameraManager.getCameraIdList()` returns a non-empty list

If both are available, Camera2 is preferred. You can override this by explicitly passing `CameraMode.CAMERA1`.

## Dynamic Configuration

`CameraProvider.updateConfig()` allows modifying camera parameters at runtime without closing or reopening the camera.

### Supported parameters

| Parameter | Camera1 | Camera2 | Effect |
|-----------|---------|---------|--------|
| `enableAutoFocus` | ✅ | ✅ | Toggle auto-focus on/off |
| `enableFlash` | ✅ | ✅ | Toggle flash mode |
| `fps` | ✅ | ✅ | Change target frame rate |
| `rotation` | ✅ | ✅ | Change display rotation |
| `camera1Config.whiteBalance` | ✅ | — | Change white balance |
| `camera1Config.sceneMode` | ✅ | — | Change scene mode |
| `camera1Config.focusMode` | ✅ | — | Change focus mode |
| `camera1Config.jpegQuality` | ✅ | — | Change JPEG quality |
| `camera2Config.controlAfMode` | — | ✅ | Override AF mode |
| `camera2Config.controlAeMode` | — | ✅ | Override AE/flash mode |
| `camera2Config.noiseReductionMode` | — | ✅ | Change noise reduction |
| `camera2Config.edgeMode` | — | ✅ | Change edge enhancement |
| `camera2Config.jpegQuality` | — | ✅ | Change JPEG quality |

> **Note:** `previewResolution`, `captureResolution`, and `analysisResolution` changes require reopening the camera. They take effect on the next `bindToLifecycle` cycle or `switchCamera` call.

### Usage

```kotlin
// Create updated config based on current settings
val newConfig = CameraConfig.Builder()
    .autoFocus(true)
    .flash(true)
    .camera1Config {
        whiteBalance(Camera1Config.WhiteBalance.DAYLIGHT)
        sceneMode(Camera1Config.SceneMode.PORTRAIT)
    }
    .build()

// Apply without interrupting preview
lifecycleScope.launch {
    provider.updateConfig(newConfig)
}
```

## Dual / Multi-Camera

Call `bindToLifecycle` multiple times with different `CameraSelector` — each call binds a set of use cases to a specific camera. All cameras share the same lifecycle.

### Usage

```kotlin
// Create use cases for each camera
val backPreview = PreviewUseCase.Builder()
    .targetResolution(Resolution.FHD)
    .build()
val backAnalysis = ImageAnalysisUseCase.Builder()
    .analysisMode(ImageAnalysisUseCase.AnalysisMode.STREAMING)
    .imageAnalyzer { /* analyze back camera frames */ }
    .build()

val frontPreview = PreviewUseCase.Builder()
    .targetResolution(Resolution.HD)
    .build()
val frontAnalysis = ImageAnalysisUseCase.Builder()
    .analysisMode(ImageAnalysisUseCase.AnalysisMode.STREAMING)
    .imageAnalyzer { /* analyze front camera frames */ }
    .build()

// Bind both cameras — each call adds one camera binding
lifecycleScope.launch {
    provider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        backPreview, backAnalysis
    )
    provider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_FRONT_CAMERA,
        frontPreview, frontAnalysis
    )
}
```

Each camera independently opens, creates a capture session, and starts its repeating request. On lifecycle stop, all cameras are closed together.

## State Machine

```
Closed → Opening → Opened → Previewing (after repeating request started)
                           ↘ Error (on exception at any stage)
```

`CameraProviderImpl` observes lifecycle events: camera and use cases start on `ON_START` and stop on `ON_STOP`.
