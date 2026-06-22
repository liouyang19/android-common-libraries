package com.taisau.android.common.camera.core

import android.content.Context
import android.view.Surface

interface ICamera {
	
	/** 相机API模式 */
	val cameraMode: CameraMode
	
	/** 初始化相机 */
	suspend fun initialize(context: Context)
	
	/** 打开指定ID的相机 */
	suspend fun open(cameraId: String): Boolean
	
	/** 关闭相机 */
	suspend fun close()
	
	/** 创建CaptureSession，会话类型通过 [CameraSession] 密封类区分 */
	suspend fun createCaptureSession(
		surfaces: List<Surface>,
		onSessionConfigured: suspend (session: CameraSession) -> Unit
	)
	
	/** 关闭CaptureSession */
	suspend fun closeCaptureSession()
	
	/** 设置重复请求（开始预览） */
	suspend fun setRepeatingRequest(surfaces: List<Surface>)
	
	/** 拍照 */
	suspend fun capture(
		surface: Surface,
		callback: (ByteArray) -> Unit
	)
	
	/** 获取相机信息 */
	fun getCameraInfo(): CameraInfo?
	
	/** 获取支持的分辨率 */
	fun getSupportedResolutions(): List<Resolution>
	
	/** 获取相机特性 */
	fun getCameraCharacteristics(): Map<String, Any>
	
	/** 相机是否已打开 */
	fun isOpen(): Boolean
	
	/** 释放所有资源 */
	suspend fun release()

    /** 获取当前相机状态 */
	fun getCameraState(): CameraState

	/** 动态更新配置参数（不影响已打开的相机和会话） */
	suspend fun updateConfig(config: CameraConfig)
}