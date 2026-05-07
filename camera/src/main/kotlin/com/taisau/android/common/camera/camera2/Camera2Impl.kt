package com.taisau.android.common.camera.camera2

import android.annotation.SuppressLint
import com.taisau.android.common.camera.core.CameraInfo
import com.taisau.android.common.camera.core.CameraState
import com.taisau.android.common.camera.core.Resolution


import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.taisau.android.common.camera.core.CameraConfig
import com.taisau.android.common.camera.core.CameraFacing
import com.taisau.android.common.camera.core.CameraMode
import com.taisau.android.common.camera.core.ICamera
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class Camera2Impl(
	private val config: CameraConfig
) : ICamera {
	
	override val cameraMode: CameraMode = CameraMode.CAMERA2
	
	private var cameraManager: CameraManager? = null
	private var cameraDevice: CameraDevice? = null
	private var captureSession: CameraCaptureSession? = null
	private var currentCameraId: String? = null
	private var cameraCharacteristics: CameraCharacteristics? = null
	private var cameraInfo: CameraInfo? = null
	
	private var backgroundThread: HandlerThread? = null
	private var backgroundHandler: Handler? = null
	private val cameraOpenCloseLock = Semaphore(1)
	
	private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
	private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	
	override fun getCameraState(): CameraState = _cameraState.value
	
	override suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
		cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
		startBackgroundThread()
	}
	
	@SuppressLint("MissingPermission")
	override suspend fun open(cameraId: String): Boolean = withContext(Dispatchers.Main) {
		try {
			_cameraState.value = CameraState.Opening
			
			if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
				throw RuntimeException("Timeout waiting to lock camera opening.")
			}
			
			closeInternal()
			
			currentCameraId = cameraId
			cameraCharacteristics = cameraManager?.getCameraCharacteristics(cameraId)
			
			// 构建CameraInfo
			buildCameraInfo()
			
			suspendCancellableCoroutine<Boolean> { continuation ->
				try {
					cameraManager?.openCamera(
						cameraId,
						object : CameraDevice.StateCallback() {
							override fun onOpened(device: CameraDevice) {
								cameraDevice = device
								_cameraState.value = CameraState.Opened
								cameraOpenCloseLock.release()
								continuation.resume(true)
							}
							
							override fun onDisconnected(device: CameraDevice) {
								device.close()
								cameraDevice = null
								cameraOpenCloseLock.release()
								_cameraState.value = CameraState.Closed
								if (continuation.isActive) {
									continuation.resume(false)
								}
							}
							
							override fun onError(device: CameraDevice, error: Int) {
								device.close()
								cameraDevice = null
								cameraOpenCloseLock.release()
								_cameraState.value = CameraState.Error(
									RuntimeException("Camera error: $error")
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
					if (continuation.isActive) {
						continuation.resumeWith(Result.failure(e))
					}
				}
			}
		} catch (e: Exception) {
			_cameraState.value = CameraState.Error(e)
			throw RuntimeException("Failed to open camera: ${e.message}", e)
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
		val device = cameraDevice ?: throw IllegalStateException("Camera not opened")
		
		suspendCancellableCoroutine { continuation ->
			try {
				device.createCaptureSession(
					surfaces,
					object : CameraCaptureSession.StateCallback() {
						override fun onConfigured(session: CameraCaptureSession) {
							captureSession = session
							scope.launch {
								try {
									onSessionConfigured(session)
									continuation.resume(Unit)
								} catch (e: Exception) {
									continuation.resumeWith(Result.failure(e))
								}
							}
						}
						
						override fun onConfigureFailed(session: CameraCaptureSession) {
							continuation.resumeWith(
								Result.failure(
									RuntimeException("Failed to configure camera session")
								)
							)
						}
					},
					backgroundHandler
				)
			} catch (e: CameraAccessException) {
				continuation.resumeWith(Result.failure(e))
			}
		}
	}
	
	override suspend fun closeCaptureSession() = withContext(Dispatchers.Main) {
		try {
			captureSession?.close()
			captureSession = null
		} catch (e: Exception) {
			throw RuntimeException("Failed to close capture session", e)
		}
	}
	
	override suspend fun setRepeatingRequest(surfaces: List<Surface>) = withContext(Dispatchers.Main) {
		val device = cameraDevice ?: throw IllegalStateException("Camera not opened")
		val session = captureSession ?: throw IllegalStateException("Capture session not created")
		
		try {
			val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
			
			surfaces.forEach { surface ->
				requestBuilder.addTarget(surface)
			}
			
			// 应用配置
			if (config.enableAutoFocus) {
				requestBuilder.set(
					CaptureRequest.CONTROL_AF_MODE,
					CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
				)
			}
			
			// 应用Camera2特定配置
			config.camera2Config?.let { camera2Config ->
				camera2Config.controlAfMode?.let {
					requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, it)
				}
				camera2Config.controlAeMode?.let {
					requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, it)
				}
				camera2Config.noiseReductionMode?.let {
					requestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, it)
				}
				camera2Config.edgeMode?.let {
					requestBuilder.set(CaptureRequest.EDGE_MODE, it)
				}
				requestBuilder.set(
					CaptureRequest.JPEG_QUALITY,
					camera2Config.jpegQuality
				)
			}
			
			session.setRepeatingRequest(
				requestBuilder.build(),
				object : CameraCaptureSession.CaptureCallback() {
					override fun onCaptureFailed(
						session: CameraCaptureSession,
						request: CaptureRequest,
						failure: CaptureFailure
					) {
						_cameraState.value = CameraState.Error(
							RuntimeException("Capture failed: $failure")
						)
					}
				},
				backgroundHandler
			)
			
			_cameraState.value = CameraState.Previewing
		} catch (e: CameraAccessException) {
			throw RuntimeException("Failed to set repeating request", e)
		}
	}
	
	override suspend fun capture(
		surface: Surface,
		callback: (ByteArray) -> Unit
	): Unit = withContext(Dispatchers.Main) {
		val device = cameraDevice ?: throw IllegalStateException("Camera not opened")
		val session = captureSession ?: throw IllegalStateException("Capture session not created")
		
		try {
			val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
			captureBuilder.addTarget(surface)
			
			if (config.enableAutoFocus) {
				captureBuilder.set(
					CaptureRequest.CONTROL_AF_MODE,
					CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
				)
			}
			
			session.capture(
				captureBuilder.build(),
				object : CameraCaptureSession.CaptureCallback() {
					override fun onCaptureCompleted(
						session: CameraCaptureSession,
						request: CaptureRequest,
						result: TotalCaptureResult
					) {
						// 拍照完成
					}
				},
				backgroundHandler
			)
		} catch (e: CameraAccessException) {
			throw RuntimeException("Failed to capture", e)
		}
	}
	
	override fun getCameraInfo(): CameraInfo? = cameraInfo
	
	override fun getSupportedResolutions(): List<Resolution> {
		val characteristics = cameraCharacteristics ?: return emptyList()
		val configMap = characteristics.get(
			CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
		)
		
		return configMap?.getOutputSizes(SurfaceTexture::class.java)?.map {
			Resolution(it.width, it.height)
		} ?: emptyList()
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
		closeInternal()
		_cameraState.value = CameraState.Closed
	}
	
	private fun closeInternal() {
		try {
			captureSession?.close()
			captureSession = null
			cameraDevice?.close()
			cameraDevice = null
		} catch (e: Exception) {
			// 忽略关闭时的异常
		}
	}
	
	override suspend fun release() = withContext(Dispatchers.Main) {
		closeInternal()
		stopBackgroundThread()
		scope.cancel()
	}
	
	private fun startBackgroundThread() {
		backgroundThread = HandlerThread("Camera2Background").also { it.start() }
		backgroundHandler = Handler(backgroundThread!!.looper)
	}
	
	private fun stopBackgroundThread() {
		backgroundThread?.quitSafely()
		try {
			backgroundThread?.join()
		} catch (e: InterruptedException) {
			e.printStackTrace()
		}
		backgroundThread = null
		backgroundHandler = null
	}
	
	// Camera2特有方法
	fun getCameraDevice(): CameraDevice? = cameraDevice
	fun getCaptureSession(): CameraCaptureSession? = captureSession
	fun getCameraCharacteristicsNative(): CameraCharacteristics? = cameraCharacteristics
}