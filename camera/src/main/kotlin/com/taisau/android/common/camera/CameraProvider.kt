package com.taisau.android.common.camera

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.taisau.android.common.camera.core.CameraConfig
import com.taisau.android.common.camera.core.CameraMode
import com.taisau.android.common.camera.core.CameraSelector
import com.taisau.android.common.camera.core.CameraState
import com.taisau.android.common.camera.core.UseCase
import kotlinx.coroutines.flow.StateFlow

abstract class CameraProvider(protected open val config: CameraConfig) {

	companion object {
		/**
		 * 创建 CameraProvider 实例。
		 * 每次调用返回独立实例，不再使用全局单例。
		 */
		@JvmStatic
		fun create(config: CameraConfig): CameraProvider {
			return CameraProviderImpl(config)
		}
	}

	abstract suspend fun initialize(context: Context)

	abstract suspend fun bindToLifecycle(
		lifecycle: LifecycleOwner,
		cameraSelector: CameraSelector,
		vararg useCases: UseCase
	)

	abstract suspend fun switchCamera(cameraSelector: CameraSelector)

	abstract suspend fun switchCameraMode(mode: CameraMode)

	abstract fun getCurrentCameraMode(): StateFlow<CameraMode>

	abstract fun getAvailableModes(): StateFlow<List<CameraMode>>

	abstract fun getCameraState(): StateFlow<CameraState>

	abstract fun isBound(): Boolean

	abstract suspend fun updateConfig(config: CameraConfig)

	abstract suspend fun release()
}