package com.taisau.android.common.camera.core

data class CameraSelector(
	val facing: CameraFacing? = null,
	val cameraId: String? = null
) {
	companion object {
		val DEFAULT_BACK = CameraSelector(facing = CameraFacing.BACK)
		val DEFAULT_FRONT = CameraSelector(facing = CameraFacing.FRONT)
	}
}