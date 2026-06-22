package com.taisau.android.common.camera.usecase

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.taisau.android.common.camera.camera1.Camera1Impl
import com.taisau.android.common.camera.camera2.Camera2Impl
import com.taisau.android.common.camera.core.ICamera
import com.taisau.android.common.camera.core.Resolution
import com.taisau.android.common.camera.core.UseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * 拍照 UseCase，支持 Camera1 和 Camera2 的静态图片捕获。
 *
 * 使用示例：
 * ```
 * val imageCapture = ImageCaptureUseCase.Builder()
 *     .setCaptureMode(CaptureMode.MAXIMIZE_QUALITY)
 *     .setFlashMode(FlashMode.AUTO)
 *     .build()
 *
 * val result = imageCapture.capture(File(cacheDir, "photo.jpg"))
 * ```
 */
class ImageCaptureUseCase private constructor(
    val targetResolution: Resolution?,
    val captureMode: CaptureMode,
    val flashMode: FlashMode,
    val jpegQuality: Int
) : UseCase() {

    enum class CaptureMode {
        MINIMIZE_LATENCY,
        MAXIMIZE_QUALITY,
        ZERO_SHUTTER_LAG
    }

    enum class FlashMode {
        AUTO, ALWAYS, OFF, ON
    }

    sealed class ImageCaptureResult {
        data class Success(val filePath: String) : ImageCaptureResult()
        data class Error(val exception: Throwable) : ImageCaptureResult()
    }

    private var currentCamera: ICamera? = null
    private var camera2CaptureReader: ImageReader? = null
    private var camera2CaptureSurface: Surface? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _captureResult = MutableStateFlow<ImageCaptureResult?>(null)
    val captureResult: StateFlow<ImageCaptureResult?> = _captureResult

    override val requiredSurfaces: List<Surface>
        get() = emptyList()

    override suspend fun onCameraOpened(camera: ICamera) {
        currentCamera = camera

        // 仅为 Camera2 创建可复用的 ImageReader，用于拍照输出
        if (camera is Camera2Impl) {
            startBackgroundThread()
            val width = targetResolution?.width ?: 1920
            val height = targetResolution?.height ?: 1080
            camera2CaptureReader = ImageReader.newInstance(
                width, height, ImageFormat.JPEG, 2
            ).also { reader ->
                camera2CaptureSurface = reader.surface
            }
        }
    }

    override suspend fun start() {
        super.start()
    }

    override suspend fun stop() {
        super.stop()
        currentCamera = null
        releaseCamera2Reader()
        stopBackgroundThread()
    }

    /**
     * 拍照并保存到文件
     *
     * @param outputFile 输出文件
     * @return 拍照结果 [ImageCaptureResult]
     */
    suspend fun capture(outputFile: File): ImageCaptureResult {
        val camera = currentCamera ?: return ImageCaptureResult.Error(
            IllegalStateException("Camera not available")
        )

        return try {
            when (camera) {
                is Camera1Impl -> captureWithCamera1(camera, outputFile)
                is Camera2Impl -> captureWithCamera2(camera, outputFile)
                else -> ImageCaptureResult.Error(
                    UnsupportedOperationException("Unsupported camera type")
                )
            }
        } catch (e: Exception) {
            ImageCaptureResult.Error(e)
        }
    }

    /**
     * Camera1 拍照：Camera1 的 capture() 忽略 surface 参数，直接使用 takePicture()
     */
    private suspend fun captureWithCamera1(camera: Camera1Impl, outputFile: File): ImageCaptureResult {
        return suspendCancellableCoroutine { continuation ->
            // camera.capture() 是 suspend 函数，需要在协程中调用
            scope.launch {
                try {
                    camera.capture(
                        // Camera1Impl.capture() 忽略 surface 参数，传入任意有效 Surface 即可
                        createDummySurface(),
                        { data ->
                            scope.launch {
                                try {
                                    FileOutputStream(outputFile).use { fos ->
                                        fos.write(data)
                                    }
                                    val result = ImageCaptureResult.Success(outputFile.absolutePath)
                                    _captureResult.value = result
                                    if (continuation.isActive) continuation.resume(result)
                                } catch (e: Exception) {
                                    val result = ImageCaptureResult.Error(e)
                                    _captureResult.value = result
                                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        val result = ImageCaptureResult.Error(e)
                        _captureResult.value = result
                        continuation.resumeWith(Result.failure(e))
                    }
                }
            }
        }
    }

    /**
     * Camera2 拍照：使用内部 ImageReader 捕获 JPEG 数据
     */
    private suspend fun captureWithCamera2(camera: Camera2Impl, outputFile: File): ImageCaptureResult {
        // 复用或创建 ImageReader
        val reader = camera2CaptureReader ?: let {
            val w = targetResolution?.width ?: 1920
            val h = targetResolution?.height ?: 1080
            ImageReader.newInstance(w, h, ImageFormat.JPEG, 1)
        }
        val surface = camera2CaptureSurface ?: reader.surface

        return suspendCancellableCoroutine { continuation ->
            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage()
                image?.let { img ->
                    try {
                        val buffer = img.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        FileOutputStream(outputFile).use { fos ->
                            fos.write(bytes)
                        }

                        val result = ImageCaptureResult.Success(outputFile.absolutePath)
                        _captureResult.value = result
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) {
                        val result = ImageCaptureResult.Error(e)
                        _captureResult.value = result
                        if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                    } finally {
                        img.close()
                    }
                }
            }, backgroundHandler)

            scope.launch {
                try {
                    camera.capture(surface) {}
                } catch (e: Exception) {
                    val result = ImageCaptureResult.Error(e)
                    _captureResult.value = result
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }
            }
        }
    }

    /** 为 Camera1 创建占位 Surface */
    private fun createDummySurface(): Surface {
        val dummyReader = ImageReader.newInstance(1, 1, ImageFormat.RGB_565, 1)
        return dummyReader.surface.apply {
            dummyReader.close()
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraCaptureBg").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    private fun releaseCamera2Reader() {
        camera2CaptureReader?.close()
        camera2CaptureReader = null
        camera2CaptureSurface = null
    }

    /**
     * 释放 UseCase 资源（非 override，由外部协调者调用）
     */
    suspend fun release() {
        stop()
        scope.cancel()
    }

    class Builder : UseCase.Builder<ImageCaptureUseCase>() {
        var targetResolution: Resolution? = null
            private set
        var captureMode: CaptureMode = CaptureMode.MAXIMIZE_QUALITY
            private set
        var flashMode: FlashMode = FlashMode.AUTO
            private set
        var jpegQuality: Int = 95
            private set

        fun setTargetResolution(resolution: Resolution) = apply { this.targetResolution = resolution }
        fun setTargetResolution(width: Int, height: Int) = apply {
            this.targetResolution = Resolution(width, height)
        }
        fun setCaptureMode(mode: CaptureMode) = apply { this.captureMode = mode }
        fun setFlashMode(mode: FlashMode) = apply { this.flashMode = mode }
        fun setJpegQuality(quality: Int) = apply {
            require(quality in 0..100) { "Quality must be 0-100" }
            this.jpegQuality = quality
        }

        override fun build(): ImageCaptureUseCase {
            return ImageCaptureUseCase(
                targetResolution = targetResolution,
                captureMode = captureMode,
                flashMode = flashMode,
                jpegQuality = jpegQuality
            )
        }
    }
}
