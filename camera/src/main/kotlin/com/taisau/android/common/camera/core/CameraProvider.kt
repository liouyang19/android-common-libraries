package com.taisau.android.common.camera.core

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.taisau.android.common.camera.CameraProviderImpl
import kotlinx.coroutines.flow.StateFlow

abstract class CameraProvider(protected open val config: CameraConfig) {

	companion object {
		@Volatile
		private var instance: CameraProvider? = null

		/**
		 * 获取 CameraProvider 单例。
		 * 首次调用时使用传入的 config 创建实例，后续调用忽略 config 参数，返回已有实例。
		 */
		@JvmStatic
		fun getInstance(config: CameraConfig): CameraProvider {
			return instance ?: synchronized(this) {
				instance ?: CameraProviderImpl(config).also { instance = it }
			}
		}

		internal fun clearInstance() {
			synchronized(this) {
				instance = null
			}
		}
	}

	abstract suspend fun initialize(context: Context)

	abstract suspend fun bindToLifecycle(
		lifecycle: LifecycleOwner,
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