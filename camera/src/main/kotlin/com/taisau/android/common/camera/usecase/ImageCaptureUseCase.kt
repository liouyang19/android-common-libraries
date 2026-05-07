package com.taisau.android.common.camera.usecase

import android.media.ImageReader
import android.view.Surface
import com.taisau.android.common.camera.camera1.Camera1Impl
import com.taisau.android.common.camera.camera2.Camera2Impl
import com.taisau.android.common.camera.core.ICamera
import com.taisau.android.common.camera.core.Resolution
import com.taisau.android.common.camera.core.UseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

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
	private var imageReader: ImageReader? = null
	private var captureSurface: Surface? = null
	
	private val _captureResult = MutableStateFlow<ImageCaptureResult?>(null)
	val captureResult: StateFlow<ImageCaptureResult?> = _captureResult
	
	override val requiredSurfaces: List<Surface>
		get() = emptyList() // 拍照不需要常驻surface
	
	override suspend fun onCameraOpened(camera: ICamera) {
		currentCamera = camera
		
		// 为Camera2创建ImageReader用于拍照
		if (camera is Camera2Impl) {
			val width = targetResolution?.width ?: 1920
			val height = targetResolution?.height ?: 1080
			imageReader = ImageReader.newInstance(
				width, height,
				android.graphics.ImageFormat.JPEG, 1
			)
			captureSurface = imageReader?.surface
			
			imageReader?.setOnImageAvailableListener({ reader ->
				val image = reader.acquireLatestImage()
				image?.let { img ->
					val buffer = img.planes[0].buffer
					val bytes = ByteArray(buffer.remaining())
					buffer.get(bytes)
					
					// 不在这里处理，由capture方法处理
					img.close()
				}
			}, null)
		}
	}
	
	override suspend fun start() {
		super.start()
	}
	
	override suspend fun stop() {
		super.stop()
		imageReader?.close()
		imageReader = null
		captureSurface = null
	}
	
	suspend fun capture(outputFile: File): ImageCaptureResult {
		val camera = currentCamera ?: return ImageCaptureResult.Error(
			IllegalStateException("Camera not available")
		)
		
		return try {
			when (camera) {
				is Camera1Impl -> captureWithCamera1(outputFile)
				is Camera2Impl -> captureWithCamera2(outputFile)
				else -> ImageCaptureResult.Error(
					UnsupportedOperationException("Unsupported camera type")
				)
			}
		} catch (e: Exception) {
			ImageCaptureResult.Error(e)
		}
	}
	
	private suspend fun captureWithCamera1(outputFile: File): ImageCaptureResult {
		val camera = currentCamera as? Camera1Impl
			?: return ImageCaptureResult.Error(IllegalStateException("Camera not available"))
		
		return suspendCancellableCoroutine { continuation ->
			try {
				// Camera1的capture使用内部方法
				camera.capture(captureSurface ?: throw IllegalStateException("No capture surface")) { data ->
					try {
						FileOutputStream(outputFile).use { fos ->
							fos.write(data)
						}
						val result = ImageCaptureResult.Success(outputFile.absolutePath)
						_captureResult.value = result
						continuation.resume(result)
					} catch (e: Exception) {
						val result = ImageCaptureResult.Error(e)
						_captureResult.value = result
						continuation.resumeWith(Result.failure(e))
					}
				}
			} catch (e: Exception) {
				val result = ImageCaptureResult.Error(e)
				_captureResult.value = result
				continuation.resumeWith(Result.failure(e))
			}
		}
	}
	
	private suspend fun captureWithCamera2(outputFile: File): ImageCaptureResult {
		val camera = currentCamera as? Camera2Impl
			?: return ImageCaptureResult.Error(IllegalStateException("Camera not available"))
		
		val surface = captureSurface
			?: return ImageCaptureResult.Error(IllegalStateException("No capture surface"))
		
		return suspendCancellableCoroutine { continuation ->
			val tempReader = ImageReader.newInstance(
				targetResolution?.width ?: 1920,
				targetResolution?.height ?: 1080,
				android.graphics.ImageFormat.JPEG,
				1
			)
			
			tempReader.setOnImageAvailableListener({ reader ->
				val image = reader.acquireLatestImage()
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
						continuation.resume(result)
					} catch (e: Exception) {
						val result = ImageCaptureResult.Error(e)
						_captureResult.value = result
						continuation.resumeWith(Result.failure(e))
					} finally {
						img.close()
						tempReader.close()
					}
				}
			}, null)
			
			camera.capture(tempReader.surface) {}
		}
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