package com.taisau.android.common.camera.core

import com.taisau.android.common.camera.camera1.Camera1Config
import com.taisau.android.common.camera.camera2.Camera2Config

data class CameraConfig(
	val cameraMode: CameraMode = CameraMode.AUTO,
	val previewResolution: Resolution = Resolution.FHD,
	val captureResolution: Resolution = Resolution.UHD,
	val analysisResolution: Resolution = Resolution.HD,
	val fps: Int = 30,
	val enableAutoFocus: Boolean = true,
	val enableFlash: Boolean = false,
	val rotation: Int = 0,
	val camera1Config: Camera1Config? = null,
	val camera2Config: Camera2Config? = null
) {
	class Builder {
		private var cameraMode: CameraMode = CameraMode.AUTO
		private var previewResolution: Resolution = Resolution.FHD
		private var captureResolution: Resolution = Resolution.UHD
		private var analysisResolution: Resolution = Resolution.HD
		private var fps: Int = 30
		private var enableAutoFocus: Boolean = true
		private var enableFlash: Boolean = false
		private var rotation: Int = 0
		private var camera1Config: Camera1Config? = null
		private var camera2Config: Camera2Config? = null
		
		fun cameraMode(mode: CameraMode) = apply { this.cameraMode = mode }
		fun previewResolution(resolution: Resolution) = apply { this.previewResolution = resolution }
		fun previewResolution(width: Int, height: Int) = apply {
			this.previewResolution = Resolution(width, height)
		}
		fun captureResolution(resolution: Resolution) = apply { this.captureResolution = resolution }
		fun captureResolution(width: Int, height: Int) = apply {
			this.captureResolution = Resolution(width, height)
		}
		fun analysisResolution(resolution: Resolution) = apply { this.analysisResolution = resolution }
		fun analysisResolution(width: Int, height: Int) = apply {
			this.analysisResolution = Resolution(width, height)
		}
		fun fps(fps: Int) = apply { this.fps = fps }
		fun autoFocus(enable: Boolean) = apply { this.enableAutoFocus = enable }
		fun flash(enable: Boolean) = apply { this.enableFlash = enable }
		fun rotation(rotation: Int) = apply { this.rotation = rotation }
		fun camera1Config(config: Camera1Config) = apply { this.camera1Config = config }
		fun camera1Config(block: Camera1Config.Builder.() -> Unit) = apply {
			this.camera1Config = Camera1Config.Builder().apply(block).build()
		}
		fun camera2Config(config: Camera2Config) = apply { this.camera2Config = config }
		fun camera2Config(block: Camera2Config.Builder.() -> Unit) = apply {
			this.camera2Config = Camera2Config.Builder().apply(block).build()
		}
		
		fun build() = CameraConfig(
			cameraMode = cameraMode,
			previewResolution = previewResolution,
			captureResolution = captureResolution,
			analysisResolution = analysisResolution,
			fps = fps,
			enableAutoFocus = enableAutoFocus,
			enableFlash = enableFlash,
			rotation = rotation,
			camera1Config = camera1Config,
			camera2Config = camera2Config
		)
	}
}