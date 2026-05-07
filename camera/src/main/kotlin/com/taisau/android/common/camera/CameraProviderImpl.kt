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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CameraProviderImpl(
	private val config: CameraConfig
) : CameraProvider {
	
	private var cameraBridge: CameraBridge? = null
	private var activeUseCases = mutableListOf<UseCase>()
	private var lifecycleOwner: LifecycleOwner? = null
	private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
	
	private val _isBound = MutableStateFlow(false)
	private val _cameraMode = MutableStateFlow(config.cameraMode)
	private val _availableModes = MutableStateFlow<List<CameraMode>>(emptyList())
	private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
	
	override suspend fun initialize(context: Context) {
		cameraBridge = CameraBridge(context, config)
		cameraBridge?.initialize()
		
		// 监听模式变化
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
	
	override suspend fun bindToLifecycle(lifecycle: LifecycleOwner, vararg useCases: UseCase) {
		// 如果已经绑定，先解绑
		if (lifecycleOwner != null) {
			unbind()
		}
		
		lifecycleOwner = lifecycle
		activeUseCases.clear()
		activeUseCases.addAll(useCases)
		
		// 监听生命周期
		lifecycle.lifecycle.addObserver(object : LifecycleEventObserver {
			override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
				when (event) {
					Lifecycle.Event.ON_START -> {
						scope.launch { startUseCases() }
					}
					Lifecycle.Event.ON_STOP -> {
						scope.launch { stopUseCases() }
					}
					Lifecycle.Event.ON_DESTROY -> {
						scope.launch {
							stopUseCases()
							unbind()
						}
					}
					else -> {}
				}
			}
		})
		
		// 如果生命周期已经处于started状态，立即启动
		if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
			startUseCases()
		}
		
		_isBound.value = true
	}
	
	
	
	
	private suspend fun startUseCases() {
		val camera = cameraBridge?.getCamera() ?: return
		
		// 打开默认摄像头
		if (!camera.isOpen()) {
			camera.open("0") // 默认打开后置摄像头
		}
		
		// 通知所有UseCase相机已打开
		activeUseCases.forEach { useCase ->
			useCase.onCameraOpened(camera)
		}
		
		// 收集所有需要的surfaces
		val allSurfaces = activeUseCases.flatMap { it.requiredSurfaces }.distinct()
		
		if (allSurfaces.isNotEmpty()) {
			camera.createCaptureSession(allSurfaces) { session ->
				camera.setRepeatingRequest(allSurfaces)
				activeUseCases.forEach { it.start() }
				_cameraState.value = CameraState.Previewing
			}
		}
	}
	
	private suspend fun stopUseCases() {
		activeUseCases.forEach { it.stop() }
		cameraBridge?.getCamera()?.closeCaptureSession()
		_cameraState.value = CameraState.Opened
	}
	
	private fun unbind() {
		lifecycleOwner = null
		activeUseCases.clear()
		_isBound.value = false
		_cameraState.value = CameraState.Closed
	}
	
	override suspend fun switchCamera(cameraSelector: CameraSelector) {
		val wasRunning = activeUseCases.any { it.isActive() }
		
		if (wasRunning) {
			stopUseCases()
		}
		
		cameraBridge?.switchCamera(cameraSelector)
		
		if (wasRunning) {
			startUseCases()
		}
	}
	
	override suspend fun switchCameraMode(mode: CameraMode) {
		val wasRunning = activeUseCases.any { it.isActive() }
		
		if (wasRunning) {
			stopUseCases()
		}
		
		cameraBridge?.switchCameraMode(mode)
		
		if (wasRunning) {
			startUseCases()
		}
	}
	
	override fun getCurrentCameraMode(): StateFlow<CameraMode> = _cameraMode
	override fun getAvailableModes(): StateFlow<List<CameraMode>> = _availableModes
	override fun getCameraState(): StateFlow<CameraState> = _cameraState
	override fun isBound(): Boolean = _isBound.value
	
	override suspend fun release() {
		stopUseCases()
		unbind()
		cameraBridge?.release()
		cameraBridge = null
		scope.cancel()
	}
}