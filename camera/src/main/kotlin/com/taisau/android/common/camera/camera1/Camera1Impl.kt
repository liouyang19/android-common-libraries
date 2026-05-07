@file:Suppress("DEPRECATION")

package com.taisau.android.common.camera.camera1
import android.content.Context
import android.hardware.Camera
import android.view.Surface
import com.taisau.android.common.camera.core.CameraConfig
import com.taisau.android.common.camera.core.CameraFacing
import com.taisau.android.common.camera.core.CameraInfo
import com.taisau.android.common.camera.core.CameraMode
import com.taisau.android.common.camera.core.CameraState
import com.taisau.android.common.camera.core.ICamera
import com.taisau.android.common.camera.core.Resolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException


class Camera1Impl(
	private val config: CameraConfig
) : ICamera {
	
	override val cameraMode: CameraMode = CameraMode.CAMERA1
	
	private var camera: Camera? = null
	private var cameraInfo: CameraInfo? = null
	private var currentCameraId: Int = -1
	private var previewCallback: ((ByteArray, Int, Int) -> Unit)? = null
	private var previewSurface: Surface? = null
	
	private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
	private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	
	override fun getCameraState(): CameraState = _cameraState.value
	
	override suspend fun initialize(context: Context) {
		// Camera1不需要特殊初始化
	}
	
	override suspend fun open(cameraId: String): Boolean = withContext(Dispatchers.Main) {
		try {
			_cameraState.value = CameraState.Opening
			
			close()
			
			val id = cameraId.toIntOrNull() ?: 0
			val numberOfCameras = Camera.getNumberOfCameras()
			
			if (id >= numberOfCameras) {
				throw IOException("Camera ID $id not found. Available: $numberOfCameras")
			}
			
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
			true
		} catch (e: Exception) {
			_cameraState.value = CameraState.Error(e)
			throw IOException("Failed to open camera: ${e.message}", e)
		}
	}
	
	private fun configureCameraParameters() {
		camera?.let { cam ->
			val params = cam.parameters
			
			// 设置预览分辨率
			val previewSize = findBestPreviewSize(
				config.previewResolution.width,
				config.previewResolution.height
			)
			params.setPreviewSize(previewSize.width, previewSize.height)
			
			// 设置拍照分辨率
			val pictureSize = findBestPictureSize(
				config.captureResolution.width,
				config.captureResolution.height
			)
			params.setPictureSize(pictureSize.width, pictureSize.height)
			
			// 设置帧率
			params.setPreviewFpsRange(config.fps * 1000, config.fps * 1000)
			
			// 设置对焦模式
			if (config.enableAutoFocus) {
				val focusMode = config.camera1Config?.focusMode ?: Camera1Config.FocusMode.AUTO
				params.focusMode = when (focusMode) {
					Camera1Config.FocusMode.AUTO -> Camera.Parameters.FOCUS_MODE_AUTO
					Camera1Config.FocusMode.MACRO -> Camera.Parameters.FOCUS_MODE_MACRO
					Camera1Config.FocusMode.INFINITY -> Camera.Parameters.FOCUS_MODE_INFINITY
					Camera1Config.FocusMode.FIXED -> Camera.Parameters.FOCUS_MODE_FIXED
					Camera1Config.FocusMode.CONTINUOUS_PICTURE -> Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
					Camera1Config.FocusMode.CONTINUOUS_VIDEO -> Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
				}
			}
			
			// 设置闪光灯
			if (config.enableFlash) {
				params.flashMode = Camera.Parameters.FLASH_MODE_AUTO
			}
			
			// 应用Camera1特定配置
			config.camera1Config?.let { camera1Config ->
				params.whiteBalance = when (camera1Config.whiteBalance) {
					Camera1Config.WhiteBalance.AUTO -> Camera.Parameters.WHITE_BALANCE_AUTO
					Camera1Config.WhiteBalance.INCANDESCENT -> Camera.Parameters.WHITE_BALANCE_INCANDESCENT
					Camera1Config.WhiteBalance.FLUORESCENT -> Camera.Parameters.WHITE_BALANCE_FLUORESCENT
					Camera1Config.WhiteBalance.DAYLIGHT -> Camera.Parameters.WHITE_BALANCE_DAYLIGHT
					Camera1Config.WhiteBalance.CLOUDY_DAYLIGHT -> Camera.Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT
				}
				
				params.sceneMode = when (camera1Config.sceneMode) {
					Camera1Config.SceneMode.AUTO -> Camera.Parameters.SCENE_MODE_AUTO
					Camera1Config.SceneMode.PORTRAIT -> Camera.Parameters.SCENE_MODE_PORTRAIT
					Camera1Config.SceneMode.LANDSCAPE -> Camera.Parameters.SCENE_MODE_LANDSCAPE
					Camera1Config.SceneMode.NIGHT -> Camera.Parameters.SCENE_MODE_NIGHT
					Camera1Config.SceneMode.SPORTS -> Camera.Parameters.SCENE_MODE_SPORTS
				}
				
				params.pictureFormat = camera1Config.pictureFormat
				params.jpegQuality = camera1Config.jpegQuality
			}
			
			cam.parameters = params
		}
	}
	
	override suspend fun createCaptureSession(
		surfaces: List<Surface>,
		onSessionConfigured: suspend (session: Any) -> Unit
	): Unit = withContext(Dispatchers.Main) {
		camera?.let { cam ->
			try {
				cam.stopPreview()
				cam.setPreviewCallback(null)
				
				// 设置预览Surface
				if (surfaces.isNotEmpty()) {
					previewSurface = surfaces[0]
					// 实际使用时需要根据Surface类型设置预览显示
					// 这里通过Camera1的setPreviewDisplay或setPreviewTexture
				}
				
				onSessionConfigured(cam)
			} catch (e: Exception) {
				throw IOException("Failed to create capture session", e)
			}
		}
	}
	
	override suspend fun closeCaptureSession(): Unit = withContext(Dispatchers.Main) {
		camera?.let { cam ->
			cam.setPreviewCallback(null)
			cam.stopPreview()
			previewSurface = null
		}
	}
	
	override suspend fun setRepeatingRequest(surfaces: List<Surface>): Unit = withContext(Dispatchers.Main) {
		camera?.let { cam ->
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
			} catch (e: Exception) {
				throw IOException("Failed to start preview", e)
			}
		}
	}
	
	override suspend fun capture(
		surface: Surface,
		callback: (ByteArray) -> Unit
	): Unit = withContext(Dispatchers.Main) {
		camera?.let { cam ->
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
		val resolutions = mutableListOf<Resolution>()
		camera?.let { cam ->
			cam.parameters.supportedPreviewSizes?.forEach { size ->
				resolutions.add(Resolution(size.width, size.height))
			}
		}
		return resolutions
	}
	
	override fun getCameraCharacteristics(): Map<String, Any> {
		val characteristics = mutableMapOf<String, Any>()
		camera?.let { cam ->
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
		camera?.let { cam ->
			try {
				cam.setPreviewCallback(null)
				cam.stopPreview()
				cam.release()
			} catch (e: Exception) {
				// 忽略关闭时的异常
			}
		}
		camera = null
		previewSurface = null
		_cameraState.value = CameraState.Closed
	}
	
	override suspend fun release() = withContext(Dispatchers.Main) {
		close()
		scope.cancel()
	}
	
	// Camera1特有方法
	fun setPreviewCallback(callback: (ByteArray, Int, Int) -> Unit) {
		previewCallback = callback
	}
	
	fun getNativeCamera(): Camera? = camera
	
	private fun findBestPreviewSize(targetWidth: Int, targetHeight: Int): Camera.Size {
		return camera?.parameters?.supportedPreviewSizes?.let { sizes ->
			sizes.minByOrNull { size ->
				kotlin.math.abs(size.width - targetWidth) + kotlin.math.abs(size.height - targetHeight)
			} ?: sizes[0]
		} ?: throw IllegalStateException("Camera not initialized")
	}
	
	private fun findBestPictureSize(targetWidth: Int, targetHeight: Int): Camera.Size {
		return camera?.parameters?.supportedPictureSizes?.let { sizes ->
			sizes.minByOrNull { size ->
				kotlin.math.abs(size.width - targetWidth) + kotlin.math.abs(size.height - targetHeight)
			} ?: sizes[0]
		} ?: throw IllegalStateException("Camera not initialized")
	}
}