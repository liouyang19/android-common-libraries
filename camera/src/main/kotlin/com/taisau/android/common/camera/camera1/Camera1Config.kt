package com.taisau.android.common.camera.camera1
data class Camera1Config(
	val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
	val sceneMode: SceneMode = SceneMode.AUTO,
	val pictureFormat: Int = android.graphics.ImageFormat.JPEG,
	val focusMode: FocusMode = FocusMode.AUTO,
	val useSmoothZoom: Boolean = false,
	val jpegQuality: Int = 95
) {
	enum class WhiteBalance {
		AUTO, INCANDESCENT, FLUORESCENT, DAYLIGHT, CLOUDY_DAYLIGHT
	}
	
	enum class SceneMode {
		AUTO, PORTRAIT, LANDSCAPE, NIGHT, SPORTS
	}
	
	enum class FocusMode {
		AUTO, MACRO, INFINITY, FIXED, CONTINUOUS_PICTURE, CONTINUOUS_VIDEO
	}
	
	class Builder {
		private var whiteBalance: WhiteBalance = WhiteBalance.AUTO
		private var sceneMode: SceneMode = SceneMode.AUTO
		private var pictureFormat: Int = android.graphics.ImageFormat.JPEG
		private var focusMode: FocusMode = FocusMode.AUTO
		private var useSmoothZoom: Boolean = false
		private var jpegQuality: Int = 95
		
		fun whiteBalance(wb: WhiteBalance) = apply { this.whiteBalance = wb }
		fun sceneMode(mode: SceneMode) = apply { this.sceneMode = mode }
		fun pictureFormat(format: Int) = apply { this.pictureFormat = format }
		fun focusMode(mode: FocusMode) = apply { this.focusMode = mode }
		fun smoothZoom(enable: Boolean) = apply { this.useSmoothZoom = enable }
		fun jpegQuality(quality: Int) = apply {
			require(quality in 0..100) { "JPEG quality must be 0-100" }
			this.jpegQuality = quality
		}
		
		fun build() = Camera1Config(
			whiteBalance = whiteBalance,
			sceneMode = sceneMode,
			pictureFormat = pictureFormat,
			focusMode = focusMode,
			useSmoothZoom = useSmoothZoom,
			jpegQuality = jpegQuality
		)
	}
}
