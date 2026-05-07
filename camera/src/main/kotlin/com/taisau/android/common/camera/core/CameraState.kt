package com.taisau.android.common.camera.core

sealed class CameraState {
	/** 相机关闭 */
	object Closed : CameraState()
	/** 相机正在打开 */
	object Opening : CameraState()
	/** 相机已打开但未预览 */
	object Opened : CameraState()
	/** 相机正在预览 */
	object Previewing : CameraState()
	/** 相机错误 */
	data class Error(val exception: Throwable) : CameraState()
}