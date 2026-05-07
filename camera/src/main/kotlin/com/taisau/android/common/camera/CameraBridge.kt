package com.taisau.android.common.camera

import android.content.Context
import com.taisau.android.common.camera.camera1.Camera1Impl
import com.taisau.android.common.camera.camera2.Camera2Impl
import com.taisau.android.common.camera.core.CameraConfig
import com.taisau.android.common.camera.core.CameraMode
import com.taisau.android.common.camera.core.CameraSelector
import com.taisau.android.common.camera.core.ICamera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CameraBridge(
	private val context: Context,
	private val config: CameraConfig
) {
	private var currentCamera: ICamera? = null
	private var currentMode: CameraMode = CameraMode.AUTO
	
	private val _cameraMode = MutableStateFlow(currentMode)
	val cameraMode: StateFlow<CameraMode> = _cameraMode
	
	private val _availableModes = MutableStateFlow<List<CameraMode>>(emptyList())
	val availableModes: StateFlow<List<CameraMode>> = _availableModes
	
	private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	
	suspend fun initialize() {
		val modes = mutableListOf(CameraMode.CAMERA1)
		
		try {
			val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
			if (manager.cameraIdList.isNotEmpty()) {
				modes.add(CameraMode.CAMERA2)
			}
		} catch (e: Exception) {
			// Camera2不可用
		}
		
		_availableModes.value = modes
		
		val actualMode = when (config.cameraMode) {
			CameraMode.AUTO -> if (modes.contains(CameraMode.CAMERA2)) CameraMode.CAMERA2 else CameraMode.CAMERA1
			else -> config.cameraMode
		}
		
		currentCamera = createCamera(actualMode)
		currentCamera?.initialize(context)
		currentMode = actualMode
		_cameraMode.value = actualMode
	}
	
	private fun createCamera(mode: CameraMode): ICamera {
		return when (mode) {
			CameraMode.CAMERA1 -> Camera1Impl(config)
			CameraMode.CAMERA2 -> Camera2Impl(config)
			CameraMode.AUTO -> throw IllegalArgumentException("Must specify explicit mode")
		}
	}
	
	suspend fun switchCameraMode(newMode: CameraMode): Boolean {
		if (newMode == currentMode) return true
		
		val availableModes = _availableModes.value
		if (!availableModes.contains(newMode)) {
			throw UnsupportedOperationException("Camera mode $newMode is not available")
		}
		
		val currentCameraInfo = currentCamera?.getCameraInfo()
		val isOpen = currentCamera?.isOpen() ?: false
		
		// 关闭当前相机
		currentCamera?.close()
		
		// 创建新相机
		currentCamera = createCamera(newMode)
		currentCamera?.initialize(context)
		currentMode = newMode
		_cameraMode.value = newMode
		
		// 如果之前是打开的，重新打开
		if (isOpen && currentCameraInfo != null) {
			currentCamera?.open(currentCameraInfo.cameraId)
		}
		
		return true
	}
	
	suspend fun switchCamera(selector: CameraSelector): Boolean {
		val cameraId = findCameraId(selector)
		if (cameraId != null) {
			// 关闭当前相机
			if (currentCamera?.isOpen() == true) {
				currentCamera?.close()
			}
			return currentCamera?.open(cameraId) ?: false
		}
		return false
	}
	
	private fun findCameraId(selector: CameraSelector): String? {
		return selector.cameraId
	}
	
	fun getCamera(): ICamera? = currentCamera
	fun getCurrentMode(): CameraMode = currentMode
	
	suspend fun release() {
		currentCamera?.release()
		currentCamera = null
		scope.cancel()
	}
}