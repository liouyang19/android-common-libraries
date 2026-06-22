package com.taisau.android.common.camera

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.taisau.android.common.camera.core.CameraConfig
import com.taisau.android.common.camera.core.CameraMode
import com.taisau.android.common.camera.core.CameraSelector
import com.taisau.android.common.camera.core.CameraSession
import com.taisau.android.common.camera.core.CameraState
import com.taisau.android.common.camera.core.ICamera
import com.taisau.android.common.camera.core.UseCase
import com.taisau.android.common.camera.utils.CameraLog
import com.taisau.android.common.camera.utils.DefaultCameraLogger
import com.taisau.android.common.camera.utils.NoLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal class CameraProviderImpl(
    override val config: CameraConfig
) : CameraProvider(config) {

	private var cameraBridge: CameraBridge? = null
	private val cameraBindings = ConcurrentHashMap<CameraSelector, List<UseCase>>()
	private var lifecycleOwner: LifecycleOwner? = null
	private var lifecycleObserver: LifecycleEventObserver? = null
	private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

	private val _isBound = MutableStateFlow(false)
	private val _cameraMode = MutableStateFlow(config.cameraMode)
	private val _availableModes = MutableStateFlow<List<CameraMode>>(emptyList())
	private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)

	override suspend fun initialize(context: Context) {
		applyLogConfig()
		cameraBridge = CameraBridge(context, config)
		cameraBridge?.initialize()

		scope.launch {
			cameraBridge?.cameraMode?.collect { mode ->
				_cameraMode.value = mode
			}
		}
		scope.launch {
			cameraBridge?.availableModes?.collect { modes ->
				_availableModes.value = modes
			}
		}
	}

	private fun applyLogConfig() {
		CameraLog.logger = when {
			config.cameraLogger != null  -> config.cameraLogger
			!config.enableLog -> NoLogger
			else -> DefaultCameraLogger()
		}
	}

	override suspend fun bindToLifecycle(
		lifecycle: LifecycleOwner,
		cameraSelector: CameraSelector,
		vararg useCases: UseCase
	) {
		// 切换到新的 LifecycleOwner：先移除旧监听器，再关相机
		if (lifecycleOwner != null && lifecycleOwner != lifecycle) {
			lifecycleObserver?.let { observer ->
				lifecycleOwner?.lifecycle?.removeObserver(observer)
			}
			lifecycleObserver = null
			cameraBridge?.closeAllCameras()
			cameraBindings.clear()
		}

		lifecycleOwner = lifecycle
		cameraBindings[cameraSelector] = useCases.toList()

		if (lifecycleObserver == null) {
			val observer = LifecycleEventObserver { _, event ->
				when (event) {
					Lifecycle.Event.ON_START -> scope.launch { startBoundCameras() }
					Lifecycle.Event.ON_STOP -> scope.launch { stopBoundCameras() }
					Lifecycle.Event.ON_DESTROY -> {
						scope.launch { release() }
					}
					else -> {}
				}
			}
			lifecycleObserver = observer
			lifecycle.lifecycle.addObserver(observer)
		}

		if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
			scope.launch {
				val _ = startSingleCamera(cameraSelector, useCases.toList())
			}
		}

		_isBound.value = true
	}

	/**
	 * 启动所有已绑定的相机
	 */
	private suspend fun startBoundCameras() {
		_cameraState.value = CameraState.Opening
		var allSucceeded = true
		for ((selector, cases) in cameraBindings.toMap()) {
			if (!startSingleCamera(selector, cases)) {
				allSucceeded = false
			}
		}
		_cameraState.value = if (allSucceeded) CameraState.Previewing else CameraState.Opened
	}

	/**
	 * 启动单个相机及其 UseCase
	 *
	 * @return true 表示全部成功
	 */
	private suspend fun startSingleCamera(selector: CameraSelector, useCases: List<UseCase>): Boolean {
		val cameraId = cameraBridge?.resolveCameraId(selector) ?: run {
			CameraLog.e("Cannot resolve camera selector: $selector")
			_cameraState.value = CameraState.Error(IllegalStateException("Cannot resolve camera selector"))
			return false
		}
		val camera = cameraBridge?.openCamera(cameraId)
		if (camera == null) {
			CameraLog.e("Cannot open camera: $cameraId")
			_cameraState.value = CameraState.Error(IllegalStateException("Cannot open camera $cameraId"))
			return false
		}
		useCases.forEach { it.onCameraOpened(camera) }
		val surfaces = useCases.flatMap { it.requiredSurfaces }.distinct()
		if (surfaces.isNotEmpty()) {
			camera.createCaptureSession(surfaces) { _: CameraSession ->
				camera.setRepeatingRequest(surfaces)
				useCases.forEach { it.start() }
			}
		}
		return true
	}

	private suspend fun stopBoundCameras() {
		cameraBindings.values.flatten().forEach { it.stop() }
		cameraBridge?.closeAllCameras()
		_cameraState.value = CameraState.Closed
	}

	/**
	 * 同步解绑：移除生命周期监听器并关闭相机
	 */
	private suspend fun unbind() {
		lifecycleObserver?.let { observer ->
			lifecycleOwner?.lifecycle?.removeObserver(observer)
		}
		lifecycleObserver = null
		lifecycleOwner = null
		cameraBridge?.closeAllCameras()
		cameraBindings.clear()
		_isBound.value = false
		_cameraState.value = CameraState.Closed
	}

	override suspend fun switchCamera(cameraSelector: CameraSelector) {
		val wasRunning = cameraBindings.values.flatten().any { it.isActive() }

		if (wasRunning) stopBoundCameras()

		cameraBindings.clear()
		val switched = cameraBridge?.switchCamera(cameraSelector) ?: false
		check(switched) { "Cannot switch camera" }
	}

	override suspend fun switchCameraMode(mode: CameraMode) {
		val wasRunning = cameraBindings.values.flatten().any { it.isActive() }

		if (wasRunning) stopBoundCameras()

		val switched = cameraBridge?.switchCameraMode(mode) ?: false
		check(switched) { "Cannot switch camera mode" }
	}

	override fun getCurrentCameraMode(): StateFlow<CameraMode> = _cameraMode
	override fun getAvailableModes(): StateFlow<List<CameraMode>> = _availableModes
	override fun getCameraState(): StateFlow<CameraState> = _cameraState
	override fun isBound(): Boolean = _isBound.value

	override suspend fun updateConfig(config: CameraConfig) {
		cameraBridge?.updateConfig(config)
	}

	override suspend fun release() {
		stopBoundCameras()
		unbind()
		cameraBridge?.release()
		cameraBridge = null
		scope.cancel()
	}
}
