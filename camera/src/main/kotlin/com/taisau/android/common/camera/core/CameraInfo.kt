package com.taisau.android.common.camera.core


data class CameraInfo(
	val cameraId: String,
	val facing: CameraFacing,
	val supportedResolutions: List<Resolution>,
	val sensorOrientation: Int
)