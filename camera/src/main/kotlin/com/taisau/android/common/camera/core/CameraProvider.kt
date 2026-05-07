package com.taisau.android.common.camera.core

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow

interface CameraProvider {
	/** 初始化 */
	suspend fun initialize(context: Context)
	
	/** 绑定到生命周期 */
	suspend fun bindToLifecycle(
		lifecycle: LifecycleOwner,
		vararg useCases: UseCase
	)
	
	/** 切换摄像头 */
	suspend fun switchCamera(cameraSelector: CameraSelector)
	
	/** 切换相机模式 */
	suspend fun switchCameraMode(mode: CameraMode)
	
	/** 获取当前相机模式 */
	fun getCurrentCameraMode(): StateFlow<CameraMode>
	
	/** 获取可用模式 */
	fun getAvailableModes(): StateFlow<List<CameraMode>>
	
	/** 获取相机状态 */
	fun getCameraState(): StateFlow<CameraState>
	
	/** 是否已绑定 */
	fun isBound(): Boolean
	
	/** 释放所有资源 */
	suspend fun release()
}