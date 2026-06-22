@file:Suppress("DEPRECATION")

package com.taisau.android.common.camera

import android.content.Context
import android.hardware.Camera
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.taisau.android.common.camera.camera1.Camera1Impl
import com.taisau.android.common.camera.camera2.Camera2Impl
import com.taisau.android.common.camera.core.CameraConfig
import com.taisau.android.common.camera.core.CameraMode
import com.taisau.android.common.camera.core.CameraSelector
import com.taisau.android.common.camera.core.ICamera
import com.taisau.android.common.camera.utils.CameraLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CameraBridge(
	private val context: Context,
	config: CameraConfig
) {

	private var config: CameraConfig = config
	private val cameras = mutableMapOf<String, ICamera>()
	private var currentMode: CameraMode = CameraMode.AUTO

	private val _cameraMode = MutableStateFlow(currentMode)
	val cameraMode: StateFlow<CameraMode> = _cameraMode

	private val _availableModes = MutableStateFlow<List<CameraMode>>(emptyList())
	val availableModes: StateFlow<List<CameraMode>> = _availableModes

	private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

	suspend fun initialize() {
		val modes = mutableListOf(CameraMode.CAMERA1)

		try {
			val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
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

		currentMode = actualMode
		_cameraMode.value = actualMode
	}

	private fun createCamera(): ICamera {
		return when (currentMode) {
			CameraMode.CAMERA1 -> Camera1Impl(config)
			CameraMode.CAMERA2 -> Camera2Impl(config)
			CameraMode.AUTO -> throw IllegalArgumentException("Must specify explicit mode")
		}
	}

	suspend fun openCamera(cameraId: String): ICamera? {
		// 如果已有该相机的有效实例，直接返回
		val existing = cameras[cameraId]
		if (existing?.isOpen() == true) return existing

		// 如果有失效实例，先移除并释放
		if (existing != null) {
			existing.close()
			cameras.remove(cameraId)
		}

		val camera = createCamera()
		camera.initialize(context)
		return if (camera.open(cameraId)) {
			cameras[cameraId] = camera
			camera
		} else {
			null
		}
	}

	suspend fun closeCamera(cameraId: String) {
		cameras.remove(cameraId)?.close()
	}

	suspend fun closeAllCameras() {
		cameras.values.toList().forEach { it.close() }
		cameras.clear()
	}

	suspend fun switchCamera(selector: CameraSelector): Boolean {
		val cameraId = findCameraId(selector) ?: return false
		closeAllCameras()
		return openCamera(cameraId) != null
	}

	suspend fun switchCameraMode(newMode: CameraMode): Boolean {
		if (newMode == currentMode) return true

		val availableModes = _availableModes.value
		if (!availableModes.contains(newMode)) {
			throw UnsupportedOperationException("Camera mode $newMode is not available")
		}

		val activeIds = cameras.keys.toList()
		closeAllCameras()

		currentMode = newMode
		_cameraMode.value = newMode

		for (id in activeIds) {
			val _ = openCamera(id)
		}

		return true
	}

	private suspend fun findCameraId(selector: CameraSelector): String? {
		selector.cameraId?.let { return it }
		selector.lensFacing?.let { facing ->
			try {
				val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
				for (id in manager.cameraIdList) {
					val chars = manager.getCameraCharacteristics(id)
					if (chars.get(CameraCharacteristics.LENS_FACING) == facing) {
						return id
					}
				}
			} catch (_: Exception) { }
			for (i in 0 until Camera.getNumberOfCameras()) {
				val info = Camera.CameraInfo()
				Camera.getCameraInfo(i, info)
				val expected = when (facing) {
					CameraCharacteristics.LENS_FACING_BACK -> Camera.CameraInfo.CAMERA_FACING_BACK
					CameraCharacteristics.LENS_FACING_FRONT -> Camera.CameraInfo.CAMERA_FACING_FRONT
					else -> -1
				}
				if (info.facing == expected) return i.toString()
			}
		}
		return null
	}

	suspend fun resolveCameraId(selector: CameraSelector): String? = findCameraId(selector)

	fun getCamera(): ICamera? = cameras.values.firstOrNull()
	fun getCamera(cameraId: String): ICamera? = cameras[cameraId]
	fun getOpenCameraIds(): Set<String> = cameras.keys
	fun getCurrentMode(): CameraMode = currentMode

	suspend fun updateConfig(config: CameraConfig) {
		this.config = config
		cameras.values.forEach { it.updateConfig(config) }
	}

	suspend fun release() {
		closeAllCameras()
		scope.cancel()
	}
}
