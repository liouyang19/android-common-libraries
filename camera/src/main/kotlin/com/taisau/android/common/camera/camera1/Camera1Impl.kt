@file:Suppress("DEPRECATION")

package com.taisau.android.common.camera.camera1
import android.content.Context
import android.hardware.Camera
import android.view.Surface
import com.taisau.android.common.camera.core.CameraConfig
import com.taisau.android.common.camera.core.CameraFacing
import com.taisau.android.common.camera.core.CameraInfo
import com.taisau.android.common.camera.core.CameraMode
import com.taisau.android.common.camera.core.CameraSession
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
import kotlinx.coroutines.withContext


class Camera1Impl(
	config: CameraConfig
) : ICamera {

	private var config: CameraConfig = config
	
	
	override val cameraMode: CameraMode = CameraMode.CAMERA1
	
	private var camera: Camera? = null
	private var cameraInfo: CameraInfo? = null
	private var currentCameraId: Int = -1
	private var previewCallback: ((ByteArray, Int, Int) -> Unit)? = null
	private var previewSurface: Surface? = null
	
	private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
	private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	
	private var supportedResolutionsCache: List<Resolution>? = null
	
	override fun getCameraState(): CameraState = _cameraState.value
	
	override suspend fun initialize(context: Context) {
		CameraLog.d("Initializing Camera1")
	}
	
	override suspend fun open(cameraId: String): Boolean = withContext(Dispatchers.IO) {
		try {
			val currentState = _cameraState.value
			if (currentState !is CameraState.Closed && currentState !is CameraState.Error) {
				CameraLog.w("Camera is not in closed state, current state: $currentState")
			}
			
			_cameraState.value = CameraState.Opening
			
			close()
			
			val id = cameraId.toIntOrNull() ?: 0
			val numberOfCameras = Camera.getNumberOfCameras()
			
			if (id >= numberOfCameras) {
				CameraLog.e("Camera ID $id not found. Available: $numberOfCameras")
				throw IllegalStateException("Camera ID $id not found. Available: $numberOfCameras")
			}
			
			CameraLog.d("Opening camera ID: $id")
			camera = Camera.open(id)
			currentCameraId = id
			
			val info = Camera.CameraInfo()
			Camera.getCameraInfo(id, info)
			
			cameraInfo = CameraInfo(
				cameraId = id.toString(),
				facing = when (info.facing) {
					Camera.CameraInfo.CAMERA_FACING_BACK -> CameraFacing.BACK
					Camera.CameraInfo.CAMERA_FACING_FRONT -> CameraFacing.FRONT
					else -> CameraFacing.BACK
				},
				supportedResolutions = getSupportedResolutions(),
				sensorOrientation = info.orientation
			)
			
			configureCameraParameters()
			
			_cameraState.value = CameraState.Opened
			CameraLog.d("Camera opened successfully, ID: $id")
			true
		} catch (e: Exception) {
			_cameraState.value = CameraState.Error(e)
			CameraLog.e("Failed to open camera: ${e.message}", e)
			throw IllegalStateException("Failed to open camera: ${e.message}", e)
		}
	}
	
	/**
	 * 配置相机参数，包括分辨率、帧率、对焦模式等
	 */
	private fun configureCameraParameters() {
        withCamera { cam ->
            val params = cam.parameters

            // 设置预览分辨率
            val previewSize = findBestPreviewSize(
                config.previewResolution.width,
                config.previewResolution.height
            )
            params.setPreviewSize(previewSize.width, previewSize.height)
            CameraLog.d("Set preview size: ${previewSize.width}x${previewSize.height}")

            // 设置拍照分辨率
            val pictureSize = findBestPictureSize(
                config.captureResolution.width,
                config.captureResolution.height
            )
            params.setPictureSize(pictureSize.width, pictureSize.height)
            CameraLog.d("Set picture size: ${pictureSize.width}x${pictureSize.height}")

            // 设置帧率
            val fpsRange = intArrayOf(config.fps * 1000, config.fps * 1000)
            params.setPreviewFpsRange(fpsRange[0], fpsRange[1])

            // 设置对焦模式
            configureFocusMode(params, config)

            // 设置闪光灯
            configureFlashMode(params, config)

            // 应用Camera1特定配置
            applyCamera1SpecificConfig(params, config.camera1Config)

            cam.parameters = params
            CameraLog.d("Camera parameters configured")
        }
	}
	
	/**
	 * 配置对焦模式
	 */
	private fun configureFocusMode(params: Camera.Parameters, config: CameraConfig) {
		if (config.enableAutoFocus) {
			val focusMode = config.camera1Config?.focusMode ?: Camera1Config.FocusMode.AUTO
			val cameraFocusMode = focusMode.toCameraFocusMode()
			
			if (params.supportedFocusModes?.contains(cameraFocusMode) == true) {
				params.focusMode = cameraFocusMode
				CameraLog.d("Focus mode set to: $focusMode")
			} else {
				CameraLog.w("Requested focus mode $focusMode not supported, using default")
			}
		}
	}
	
	/**
	 * 配置闪光灯模式
	 */
	private fun configureFlashMode(params: Camera.Parameters, config: CameraConfig) {
		if (config.enableFlash) {
			if (params.supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_AUTO) == true) {
				params.flashMode = Camera.Parameters.FLASH_MODE_AUTO
				CameraLog.d("Flash mode set to AUTO")
			} else {
				CameraLog.w("Flash mode AUTO not supported")
			}
		}
	}
	
	/**
	 * 应用Camera1特定的配置项
	 */
	private fun applyCamera1SpecificConfig(params: Camera.Parameters, camera1Config: Camera1Config?) {
		camera1Config?.let { cfg ->
			val whiteBalance = cfg.whiteBalance.toCameraWhiteBalance()
			if (params.supportedWhiteBalance?.contains(whiteBalance) == true) {
				params.whiteBalance = whiteBalance
			} else {
				CameraLog.w("White balance $whiteBalance not supported")
			}
			
			val sceneMode = cfg.sceneMode.toCameraSceneMode()
			if (params.supportedSceneModes?.contains(sceneMode) == true) {
				params.sceneMode = sceneMode
			} else {
				CameraLog.w("Scene mode $sceneMode not supported")
			}
			
			params.pictureFormat = cfg.pictureFormat
			params.jpegQuality = cfg.jpegQuality
		}
	}
	
	override suspend fun createCaptureSession(
		surfaces: List<Surface>,
		onSessionConfigured: suspend (session: CameraSession) -> Unit
	): Unit = withContext(Dispatchers.Main) {
		withCamera { cam ->
			try {
				CameraLog.d("Creating capture session")
				cam.stopPreview()
				cam.setPreviewCallback(null)
				
				// 设置预览Surface
				if (surfaces.isNotEmpty()) {
					previewSurface = surfaces[0]
				}
				
				onSessionConfigured(CameraSession.Camera1Session(cam))
				CameraLog.d("Capture session created")
			} catch (e: Exception) {
				CameraLog.e("Failed to create capture session", e)
				throw IllegalStateException("Failed to create capture session", e)
			}
		}
	}
	
	override suspend fun closeCaptureSession(): Unit = withContext(Dispatchers.Main) {
		withCamera { cam ->
			CameraLog.d("Closing capture session")
			cam.setPreviewCallback(null)
			cam.stopPreview()
			previewSurface = null
		}
	}
	
	override suspend fun setRepeatingRequest(surfaces: List<Surface>): Unit = withContext(Dispatchers.Main) {
		withCamera { cam ->
			try {
				if (previewCallback != null) {
					cam.setPreviewCallback { data, camera ->
						data?.let { bytes ->
							val size = camera.parameters.previewSize
							scope.launch(Dispatchers.Default) {
								previewCallback?.invoke(bytes, size.width, size.height)
							}
						}
					}
				}
				cam.startPreview()
				_cameraState.value = CameraState.Previewing
				CameraLog.d("Preview started")
			} catch (e: Exception) {
				CameraLog.e("Failed to start preview", e)
				throw IllegalStateException("Failed to start preview", e)
			}
		}
	}
	
	override suspend fun capture(
		surface: Surface,
		callback: (ByteArray) -> Unit
	): Unit = withContext(Dispatchers.Main) {
		withCamera { cam ->
			CameraLog.d("Capturing photo")
			cam.takePicture(
				{ /* shutter */ },
				{ _, _ -> /* raw */},
				{ data, _ ->
					scope.launch(Dispatchers.IO) {
						callback(data)
					}
				}
			)
		}
	}
	
	override fun getCameraInfo(): CameraInfo? = cameraInfo
	
	override fun getSupportedResolutions(): List<Resolution> {
		supportedResolutionsCache?.let { return it }
		
		val resolutions = mutableListOf<Resolution>()
		withCamera { cam ->
			cam.parameters.supportedPreviewSizes?.forEach { size ->
				resolutions.add(Resolution(size.width, size.height))
			}
			supportedResolutionsCache = resolutions
			CameraLog.d("Found ${resolutions.size} supported resolutions")
		}
		return resolutions
	}
	
	override fun getCameraCharacteristics(): Map<String, Any> {
		val characteristics = mutableMapOf<String, Any>()
		withCamera { cam ->
			val params = cam.parameters
			characteristics["previewSizes"] = params.supportedPreviewSizes ?: emptyList<Camera.Size>()
			characteristics["pictureSizes"] = params.supportedPictureSizes ?: emptyList<Camera.Size>()
			characteristics["focusModes"] = params.supportedFocusModes ?: emptyList<String>()
			characteristics["maxZoom"] = params.maxZoom
			characteristics["isZoomSupported"] = params.isZoomSupported
			characteristics["isSmoothZoomSupported"] = params.isSmoothZoomSupported
		}
		return characteristics
	}
	
	override fun isOpen(): Boolean = camera != null
	
	override suspend fun close() = withContext(Dispatchers.Main) {
		CameraLog.d("Closing camera")
		camera?.let { cam ->
			try {
				cam.setPreviewCallback(null)
				cam.stopPreview()
				cam.release()
				CameraLog.d("Camera released")
			} catch (e: Exception) {
				CameraLog.e("Error releasing camera", e)
			}
		}
		camera = null
		previewSurface = null
		previewCallback = null
		supportedResolutionsCache = null
		_cameraState.value = CameraState.Closed
	}
	
	override suspend fun release(){
		withContext(Dispatchers.Main) {
			CameraLog.d("Releasing camera resources")
			close()
			scope.cancel()
			CameraLog.d("Camera resources released")
		}
	}

	override suspend fun updateConfig(config: CameraConfig) = withContext(Dispatchers.Main) {
		this@Camera1Impl.config = config
		if (camera != null) {
			CameraLog.d("Applying updated camera parameters")
			configureCameraParameters()
		}
	}
	
	// Camera1特有方法
	fun setPreviewCallback(callback: (ByteArray, Int, Int) -> Unit) {
		previewCallback = callback
	}
	
	fun getNativeCamera(): Camera? = camera
	
	/**
	 * 查找最合适的预览尺寸
	 */
	private fun findBestPreviewSize(targetWidth: Int, targetHeight: Int): Camera.Size {
		return withCamera { cam ->
			cam.parameters.supportedPreviewSizes?.let { sizes ->
				findBestSize(sizes, targetWidth, targetHeight)
			} ?: throw IllegalStateException("No supported preview sizes available")
		}
	}
	
	/**
	 * 查找最合适的拍照尺寸
	 */
	private fun findBestPictureSize(targetWidth: Int, targetHeight: Int): Camera.Size {
		return withCamera { cam ->
			cam.parameters.supportedPictureSizes?.let { sizes ->
				findBestSize(sizes, targetWidth, targetHeight)
			} ?: throw IllegalStateException("No supported picture sizes available")
		}
	}
	
	/**
	 * 从尺寸列表中查找最匹配的尺寸
	 */
	private fun findBestSize(sizes: List<Camera.Size>, targetWidth: Int, targetHeight: Int): Camera.Size {
		return sizes.minByOrNull { size ->
			kotlin.math.abs(size.width - targetWidth) + kotlin.math.abs(size.height - targetHeight)
		} ?: sizes[0]
	}
	
	/**
	 * 执行需要Camera的操作，如果Camera未初始化则抛出异常
	 */
	private inline fun <T> withCamera(block: (Camera) -> T): T {
		val cam = camera ?: throw IllegalStateException("Camera is not initialized")
		return block(cam)
	}


}

/**
 * 将Camera1Config的白平衡枚举转换为Camera API的常量
 */
private fun Camera1Config.WhiteBalance.toCameraWhiteBalance(): String = when (this) {
	Camera1Config.WhiteBalance.AUTO -> Camera.Parameters.WHITE_BALANCE_AUTO
	Camera1Config.WhiteBalance.INCANDESCENT -> Camera.Parameters.WHITE_BALANCE_INCANDESCENT
	Camera1Config.WhiteBalance.FLUORESCENT -> Camera.Parameters.WHITE_BALANCE_FLUORESCENT
	Camera1Config.WhiteBalance.DAYLIGHT -> Camera.Parameters.WHITE_BALANCE_DAYLIGHT
	Camera1Config.WhiteBalance.CLOUDY_DAYLIGHT -> Camera.Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT
}

/**
 * 将Camera1Config的场景模式枚举转换为Camera API的常量
 */
private fun Camera1Config.SceneMode.toCameraSceneMode(): String = when (this) {
	Camera1Config.SceneMode.AUTO -> Camera.Parameters.SCENE_MODE_AUTO
	Camera1Config.SceneMode.PORTRAIT -> Camera.Parameters.SCENE_MODE_PORTRAIT
	Camera1Config.SceneMode.LANDSCAPE -> Camera.Parameters.SCENE_MODE_LANDSCAPE
	Camera1Config.SceneMode.NIGHT -> Camera.Parameters.SCENE_MODE_NIGHT
	Camera1Config.SceneMode.SPORTS -> Camera.Parameters.SCENE_MODE_SPORTS
}

/**
 * 将Camera1Config的对焦模式枚举转换为Camera API的常量
 */
private fun Camera1Config.FocusMode.toCameraFocusMode(): String = when (this) {
	Camera1Config.FocusMode.AUTO -> Camera.Parameters.FOCUS_MODE_AUTO
	Camera1Config.FocusMode.MACRO -> Camera.Parameters.FOCUS_MODE_MACRO
	Camera1Config.FocusMode.INFINITY -> Camera.Parameters.FOCUS_MODE_INFINITY
	Camera1Config.FocusMode.FIXED -> Camera.Parameters.FOCUS_MODE_FIXED
	Camera1Config.FocusMode.CONTINUOUS_PICTURE -> Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
	Camera1Config.FocusMode.CONTINUOUS_VIDEO -> Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
}
