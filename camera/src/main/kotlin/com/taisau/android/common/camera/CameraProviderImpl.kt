package com.taisau.android.common.camera

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.taisau.android.common.camera.core.CameraConfig
import com.taisau.android.common.camera.core.CameraMode
import com.taisau.android.common.camera.core.CameraProvider
import com.taisau.android.common.camera.core.CameraSelector
import com.taisau.android.common.camera.core.CameraState
import com.taisau.android.common.camera.core.UseCase
import com.taisau.android.common.camera.uitls.CameraLog
import com.taisau.android.common.camera.uitls.DefaultCameraLogger
import com.taisau.android.common.camera.uitls.NoLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

internal class CameraProviderImpl(
    override val config: CameraConfig
) : CameraProvider(config) {

	private var cameraBridge: CameraBridge? = null
	private val cameraBindings = mutableMapOf<CameraSelector, List<UseCase>>()
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
		if (lifecycleOwner != null && lifecycleOwner != lifecycle) {
			unbind()
		}

		lifecycleOwner = lifecycle
		cameraBindings[cameraSelector] = useCases.toList()

		if (lifecycleObserver == null) {
			val observer = LifecycleEventObserver { _, event ->
				when (event) {
					Lifecycle.Event.ON_START -> scope.launch { startAllCameras() }
					Lifecycle.Event.ON_STOP -> scope.launch { stopAllCameras() }
					Lifecycle.Event.ON_DESTROY -> scope.launch { unbind() }
					else -> {}
				}
			}
			lifecycleObserver = observer
			lifecycle.lifecycle.addObserver(observer)
		}

		if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
			scope.launch { startCamera(cameraSelector, useCases.toList()) }
		}

		_isBound.value = true
	}

	private suspend fun startAllCameras() {
		var allSucceeded = true
		for ((selector, cases) in cameraBindings.toMap()) {
			val cameraId = cameraBridge?.resolveCameraId(selector) ?: run {
				CameraLog.e("无法解析 cameraSelector: $selector")
				allSucceeded = false
				continue
			}
			val camera = cameraBridge?.openCamera(cameraId)
			if (camera == null) {
				CameraLog.e("无法打开相机 $cameraId")
				allSucceeded = false
				continue
			}
			cases.forEach { it.onCameraOpened(camera) }
			val surfaces = cases.flatMap { it.requiredSurfaces }.distinct()
			if (surfaces.isNotEmpty()) {
				camera.createCaptureSession(surfaces) { _ ->
					camera.setRepeatingRequest(surfaces)
					cases.forEach { it.start() }
				}
			}
		}
		_cameraState.value = if (allSucceeded) CameraState.Previewing else CameraState.Opened
	}

	private suspend fun startCamera(selector: CameraSelector, useCases: List<UseCase>) {
		val cameraId = cameraBridge?.resolveCameraId(selector) ?: run {
			CameraLog.e("无法解析 cameraSelector: $selector")
			_cameraState.value = CameraState.Error(RuntimeException("Cannot resolve camera selector"))
			return
		}
		val camera = cameraBridge?.openCamera(cameraId)
		if (camera == null) {
			CameraLog.e("无法打开相机 $cameraId")
			_cameraState.value = CameraState.Error(RuntimeException("Cannot open camera $cameraId"))
			return
		}
		useCases.forEach { it.onCameraOpened(camera) }
		val surfaces = useCases.flatMap { it.requiredSurfaces }.distinct()
		if (surfaces.isNotEmpty()) {
			camera.createCaptureSession(surfaces) { _ ->
				camera.setRepeatingRequest(surfaces)
				useCases.forEach { it.start() }
				_cameraState.value = CameraState.Previewing
			}
		}
	}

	private suspend fun stopAllCameras() {
		cameraBindings.values.flatten().forEach { it.stop() }
		cameraBridge?.closeAllCameras()
		_cameraState.value = CameraState.Closed
	}

	private fun unbind() {
		lifecycleObserver?.let { observer ->
			lifecycleOwner?.lifecycle?.removeObserver(observer)
		}
		lifecycleObserver = null
		lifecycleOwner = null
		scope.launch { cameraBridge?.closeAllCameras() }
		cameraBindings.clear()
		_isBound.value = false
		_cameraState.value = CameraState.Closed
	}

	override suspend fun switchCamera(cameraSelector: CameraSelector) {
		val wasRunning = cameraBindings.values.flatten().any { it.isActive() }

		if (wasRunning) stopAllCameras()

		cameraBindings.clear()
		val switched = cameraBridge?.switchCamera(cameraSelector) ?: false
		check(switched) { "无法切换相机" }
	}

	override suspend fun switchCameraMode(mode: CameraMode) {
		val wasRunning = cameraBindings.values.flatten().any { it.isActive() }

		if (wasRunning) stopAllCameras()

		val switched = cameraBridge?.switchCameraMode(mode) ?: false
		check(switched) { "无法切换相机模式" }
	}

	override fun getCurrentCameraMode(): StateFlow<CameraMode> = _cameraMode
	override fun getAvailableModes(): StateFlow<List<CameraMode>> = _availableModes
	override fun getCameraState(): StateFlow<CameraState> = _cameraState
	override fun isBound(): Boolean = _isBound.value

	override suspend fun updateConfig(config: CameraConfig) {
		cameraBridge?.updateConfig(config)
	}

	override suspend fun release() {
		stopAllCameras()
		unbind()
		cameraBridge?.release()
		cameraBridge = null
		scope.cancel()
	}
}
