package com.taisau.android.common.camera.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import com.taisau.android.common.camera.core.CameraConfig
import com.taisau.android.common.camera.core.CameraFacing
import com.taisau.android.common.camera.core.CameraInfo
import com.taisau.android.common.camera.core.CameraMode
import com.taisau.android.common.camera.core.CameraState
import com.taisau.android.common.camera.core.ICamera
import com.taisau.android.common.camera.core.Resolution
import com.taisau.android.common.camera.utils.CameraLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class Camera2Impl(
	config: CameraConfig
) : ICamera {

	private var config: CameraConfig = config
	
	companion object {
		private const val CAMERA_LOCK_TIMEOUT_MS = 2500L
	}
	
	override val cameraMode: CameraMode = CameraMode.CAMERA2
	
	private var cameraManager: CameraManager? = null
	private var cameraDevice: CameraDevice? = null
	private var captureSession: CameraCaptureSession? = null
	private var currentCameraId: String? = null
	private var cameraCharacteristics: CameraCharacteristics? = null
	private var cameraInfo: CameraInfo? = null
	private var imageReader: ImageReader? = null
	private var previewSurfaces: List<Surface>? = null
	
	private var backgroundThread: HandlerThread? = null
	private var backgroundHandler: Handler? = null
	private val cameraOpenCloseLock = Semaphore(1)
	
	private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
	private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	
	override fun getCameraState(): CameraState = _cameraState.value
	
	override suspend fun initialize(context: Context) = withContext(Dispatchers.Main) {
		CameraLog.d("Initializing Camera2")
		cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
		startBackgroundThread()
	}
	
	@SuppressLint("MissingPermission")
	override suspend fun open(cameraId: String): Boolean = withContext(Dispatchers.Main) {
		try {
			val currentState = _cameraState.value
			if (currentState !is CameraState.Closed && currentState !is CameraState.Error) {
				CameraLog.w("Camera is not in closed state, current state: $currentState")
			}
			
			_cameraState.value = CameraState.Opening
			
			val acquired = withContext(Dispatchers.IO) {
				cameraOpenCloseLock.tryAcquire(CAMERA_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
			}
			if (!acquired) {
				throw RuntimeException("Timeout waiting to lock camera opening.")
			}
			
			closeInternal()
			
			currentCameraId = cameraId
			cameraCharacteristics = cameraManager?.getCameraCharacteristics(cameraId)
			
			buildCameraInfo()
			
			CameraLog.d("Opening camera ID: $cameraId")
			suspendCancellableCoroutine { continuation ->
				try {
					cameraManager?.openCamera(
						cameraId,
						object : CameraDevice.StateCallback() {
							override fun onOpened(device: CameraDevice) {
								cameraDevice = device
								_cameraState.value = CameraState.Opened
								cameraOpenCloseLock.release()
								CameraLog.d("Camera opened successfully, ID: $cameraId")
								continuation.resume(true)
							}
							
							override fun onDisconnected(device: CameraDevice) {
								device.close()
								cameraDevice = null
								cameraOpenCloseLock.release()
								_cameraState.value = CameraState.Closed
								CameraLog.w("Camera disconnected")
								if (continuation.isActive) {
									continuation.resume(false)
								}
							}
							
							override fun onError(device: CameraDevice, error: Int) {
								device.close()
								cameraDevice = null
								cameraOpenCloseLock.release()
								val errorMsg = "Camera error: $error"
								CameraLog.e(errorMsg)
								_cameraState.value = CameraState.Error(
									RuntimeException(errorMsg)
								)
								if (continuation.isActive) {
									continuation.resume(false)
								}
							}
						},
						backgroundHandler
					)
				} catch (e: CameraAccessException) {
					cameraOpenCloseLock.release()
					_cameraState.value = CameraState.Error(e)
					CameraLog.e("CameraAccessException while opening camera", e)
					if (continuation.isActive) {
						continuation.resumeWith(Result.failure(e))
					}
				}
			}
		} catch (e: Exception) {
			_cameraState.value = CameraState.Error(e)
			val errorMsg = "Failed to open camera: ${e.message}"
			CameraLog.e(errorMsg, e)
			throw RuntimeException(errorMsg, e)
		}
	}
	
	private fun buildCameraInfo() {
		val characteristics = cameraCharacteristics ?: return
		val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
			CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.BACK
			CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.FRONT
			else -> CameraFacing.BACK
		}
		
		cameraInfo = CameraInfo(
			cameraId = currentCameraId ?: "",
			facing = facing,
			supportedResolutions = getSupportedResolutions(),
			sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
		)
	}
	
	override suspend fun createCaptureSession(
		surfaces: List<Surface>,
		onSessionConfigured: suspend (session: Any) -> Unit
	) = withContext(Dispatchers.Main) {
		ensureCameraOpened()
		val device = cameraDevice ?: return@withContext
		
		CameraLog.d("Creating capture session with ${surfaces.size} surface(s)")
		suspendCancellableCoroutine { continuation ->
			try {
				val stateCallback = object : CameraCaptureSession.StateCallback() {
					override fun onConfigured(session: CameraCaptureSession) {
						captureSession = session
						scope.launch {
							try {
								onSessionConfigured(session)
								CameraLog.d("Capture session created")
								continuation.resume(Unit)
							} catch (e: Exception) {
								continuation.resumeWith(Result.failure(e))
							}
						}
					}
					
					override fun onConfigureFailed(session: CameraCaptureSession) {
						val errorMsg = "Failed to configure camera session"
						CameraLog.e(errorMsg)
						continuation.resumeWith(
							Result.failure(RuntimeException(errorMsg))
						)
					}
				}
				
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
					val executor = Executors.newSingleThreadExecutor()
					val outputConfigs = surfaces.map { OutputConfiguration(it) }
					val sessionConfig = SessionConfiguration(
						SessionConfiguration.SESSION_REGULAR,
						outputConfigs,
						executor,
						stateCallback
					)
					device.createCaptureSession(sessionConfig)
				} else {
					@Suppress("DEPRECATION")
					device.createCaptureSession(surfaces, stateCallback, backgroundHandler)
				}
			} catch (e: CameraAccessException) {
				CameraLog.e("CameraAccessException while creating capture session", e)
				continuation.resumeWith(Result.failure(e))
			}
		}
	}
	
	override suspend fun closeCaptureSession() = withContext(Dispatchers.Main) {
		CameraLog.d("Closing capture session")
		try {
			captureSession?.close()
			captureSession = null
		} catch (e: Exception) {
			CameraLog.e("Failed to close capture session", e)
			throw RuntimeException("Failed to close capture session", e)
		}
	}
	
	override suspend fun setRepeatingRequest(surfaces: List<Surface>) = withContext(Dispatchers.Main) {
		ensureCameraOpened()
		val session = captureSession ?: throw IllegalStateException("Capture session not created")
		val device = cameraDevice ?: return@withContext

		previewSurfaces = surfaces

		try {
			buildAndSetRepeatingRequest(device, session, surfaces)
			
			_cameraState.value = CameraState.Previewing
			CameraLog.d("Repeating request set, preview started")
		} catch (e: CameraAccessException) {
			CameraLog.e("Failed to set repeating request", e)
			throw RuntimeException("Failed to set repeating request", e)
		}
	}

	private fun buildAndSetRepeatingRequest(
		device: CameraDevice,
		session: CameraCaptureSession,
		surfaces: List<Surface>
	) {
		val requestBuilder = device.createCaptureRequest(
			config.camera2Config?.captureRequestTemplate?.toCameraTemplate()
				?: CameraDevice.TEMPLATE_PREVIEW
		)
		surfaces.forEach { requestBuilder.addTarget(it) }
		applyCaptureRequestConfig(requestBuilder, config)
		session.setRepeatingRequest(
			requestBuilder.build(),
			object : CameraCaptureSession.CaptureCallback() {
				override fun onCaptureFailed(
					session: CameraCaptureSession,
					request: CaptureRequest,
					failure: CaptureFailure
				) {
					val errorMsg = "Capture failed: $failure"
					CameraLog.e(errorMsg)
					_cameraState.value = CameraState.Error(
						RuntimeException(errorMsg)
					)
				}
			},
			backgroundHandler
		)
	}
	
	override suspend fun capture(
		surface: Surface,
		callback: (ByteArray) -> Unit
	): Unit = withContext(Dispatchers.Main) {
		ensureCameraOpened()
		val session = captureSession ?: throw IllegalStateException("Capture session not created")
		val device = cameraDevice ?: return@withContext

		try {
			CameraLog.d("Capturing photo")

			val captureWidth = config.captureResolution.width
			val captureHeight = config.captureResolution.height

			// 关闭旧的 ImageReader 防止泄漏
			imageReader?.close()
			imageReader = ImageReader.newInstance(
				captureWidth, captureHeight,
				ImageFormat.JPEG, 2
			).also { reader ->
				reader.setOnImageAvailableListener({ r ->
					val image = r.acquireLatestImage()
					image?.use { img ->
						val buffer = img.planes[0].buffer
						val bytes = ByteArray(buffer.remaining())
						buffer.get(bytes)
						scope.launch(Dispatchers.IO) {
							callback(bytes)
						}
					}
				}, backgroundHandler)
			}
			
			val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
			captureBuilder.addTarget(imageReader!!.surface)
			captureBuilder.addTarget(surface)
			
			applyCaptureRequestConfig(captureBuilder, config)
			
			captureBuilder.set(
				CaptureRequest.JPEG_ORIENTATION,
				cameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
			)
			
			session.capture(
				captureBuilder.build(),
				object : CameraCaptureSession.CaptureCallback() {
					override fun onCaptureCompleted(
						session: CameraCaptureSession,
						request: CaptureRequest,
						result: TotalCaptureResult
					) {
						CameraLog.d("Capture completed")
					}
					
					override fun onCaptureFailed(
						session: CameraCaptureSession,
						request: CaptureRequest,
						failure: CaptureFailure
					) {
						CameraLog.e("Capture failed: reason=${failure.reason}")
					}
				},
				backgroundHandler
			)
		} catch (e: CameraAccessException) {
			CameraLog.e("Failed to capture", e)
			throw RuntimeException("Failed to capture", e)
		}
	}
	
	override fun getCameraInfo(): CameraInfo? = cameraInfo
	
	override fun getSupportedResolutions(): List<Resolution> {
		val characteristics = cameraCharacteristics ?: return emptyList()
		val configMap = characteristics.get(
			CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
		) ?: return emptyList()

		val resolutions = mutableSetOf<Resolution>()

		// 预览输出尺寸（SurfaceTexture）
		configMap.getOutputSizes(SurfaceTexture::class.java)?.forEach {
			resolutions.add(Resolution(it.width, it.height))
		}
		// 拍照输出尺寸（JPEG）
		configMap.getOutputSizes(ImageFormat.JPEG)?.forEach {
			resolutions.add(Resolution(it.width, it.height))
		}

		return resolutions.toList().sortedByDescending { it.width * it.height }
	}
	
	override fun getCameraCharacteristics(): Map<String, Any> {
		val characteristics = cameraCharacteristics ?: return emptyMap()
		return mapOf(
			"lensFacing" to (characteristics.get(CameraCharacteristics.LENS_FACING) ?: ""),
			"sensorOrientation" to (characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0),
			"hardwareLevel" to (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: ""),
			"flashAvailable" to (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false)
		)
	}
	
	override fun isOpen(): Boolean = cameraDevice != null
	
	override suspend fun close() = withContext(Dispatchers.Main) {
		CameraLog.d("Closing camera")
		val acquired = withContext(Dispatchers.IO) {
			cameraOpenCloseLock.tryAcquire(CAMERA_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
		}
		if (!acquired) {
			CameraLog.w("Timeout waiting to lock camera closing")
		}
		try {
			closeInternal()
		} finally {
			cameraOpenCloseLock.release()
		}
		imageReader?.close()
		imageReader = null
		_cameraState.value = CameraState.Closed
	}
	
	private fun closeInternal() {
		try {
			captureSession?.close()
			captureSession = null
			cameraDevice?.close()
			cameraDevice = null
		} catch (e: Exception) {
			CameraLog.e("Error during closeInternal", e)
		}
	}
	
	override suspend fun release() {
		withContext(Dispatchers.Main) {
			CameraLog.d("Releasing camera resources")
			val acquired = withContext(Dispatchers.IO) {
				cameraOpenCloseLock.tryAcquire(CAMERA_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
			}
			if (!acquired) {
				CameraLog.w("Timeout waiting to lock camera release")
			}
			try {
				closeInternal()
			} finally {
				cameraOpenCloseLock.release()
			}
			imageReader?.close()
			imageReader = null
			stopBackgroundThread()
			scope.cancel()
			CameraLog.d("Camera resources released")
		}
	}

	override suspend fun updateConfig(config: CameraConfig) = withContext(Dispatchers.Main) {
		this@Camera2Impl.config = config
		CameraLog.d("Camera config updated dynamically")
		val device = cameraDevice
		val session = captureSession
		val surfaces = previewSurfaces
		if (device != null && session != null && surfaces != null) {
			try {
				buildAndSetRepeatingRequest(device, session, surfaces)
				CameraLog.d("Repeating request updated with new config")
			} catch (e: CameraAccessException) {
				CameraLog.e("Failed to update repeating request", e)
			}
		}
	}
	
	private fun startBackgroundThread() {
		backgroundThread = HandlerThread("Camera2Background").also { it.start() }
		backgroundHandler = Handler(backgroundThread!!.looper)
		CameraLog.d("Background thread started")
	}
	
	private fun stopBackgroundThread() {
		backgroundThread?.quitSafely()
		try {
			backgroundThread?.join()
			CameraLog.d("Background thread stopped")
		} catch (e: InterruptedException) {
			CameraLog.e("Error stopping background thread", e)
		}
		backgroundThread = null
		backgroundHandler = null
	}
	
	private fun ensureCameraOpened() {
		if (cameraDevice == null) {
			throw IllegalStateException("Camera not opened")
		}
	}
	

	/**
	 * 将Camera2Config的模板类型转换为CameraDevice模板常量
	 */
	private fun Camera2Config.TemplateType.toCameraTemplate(): Int = when (this) {
		Camera2Config.TemplateType.PREVIEW -> CameraDevice.TEMPLATE_PREVIEW
		Camera2Config.TemplateType.STILL_CAPTURE -> CameraDevice.TEMPLATE_STILL_CAPTURE
		Camera2Config.TemplateType.RECORD -> CameraDevice.TEMPLATE_RECORD
		Camera2Config.TemplateType.ZERO_SHUTTER_LAG -> CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG
	}
	
	/**
	 * 将公共配置和Camera2特定配置应用到CaptureRequest.Builder
	 */
	private fun applyCaptureRequestConfig(builder: CaptureRequest.Builder, config: CameraConfig) {
		// 设置自动对焦
		if (config.enableAutoFocus) {
			builder.set(
				CaptureRequest.CONTROL_AF_MODE,
				CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
			)
		}

		// 设置闪光灯
		if (config.enableFlash) {
			builder.set(
				CaptureRequest.CONTROL_AE_MODE,
				CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
			)
		}

		// 设置目标帧率（FPS）
		builder.set(
			CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
			Range(config.fps, config.fps)
		)

		// 设置预览旋转角度
		builder.set(CaptureRequest.JPEG_ORIENTATION, config.rotation)

		// 应用 Camera2 特定配置
		config.camera2Config?.let { camera2Config ->
			camera2Config.controlAfMode?.let {
				builder.set(CaptureRequest.CONTROL_AF_MODE, it)
			}
			camera2Config.controlAeMode?.let {
				builder.set(CaptureRequest.CONTROL_AE_MODE, it)
			}
			camera2Config.noiseReductionMode?.let {
				builder.set(CaptureRequest.NOISE_REDUCTION_MODE, it)
			}
			camera2Config.edgeMode?.let {
				builder.set(CaptureRequest.EDGE_MODE, it)
			}
			builder.set(CaptureRequest.JPEG_QUALITY, camera2Config.jpegQuality.toByte())
		}
	}
	
	// Camera2特有方法
	fun getCameraDevice(): CameraDevice? = cameraDevice
	fun getCaptureSession(): CameraCaptureSession? = captureSession
	fun getCameraCharacteristicsNative(): CameraCharacteristics? = cameraCharacteristics
}