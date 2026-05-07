package com.taisau.android.common.camera.camera2

data class Camera2Config(
	val hardwareLevel: HardwareLevel = HardwareLevel.FULL,
	val captureRequestTemplate: TemplateType = TemplateType.PREVIEW,
	val controlAfMode: Int? = null,
	val controlAeMode: Int? = null,
	val noiseReductionMode: Int? = null,
	val edgeMode: Int? = null,
	val useReprocessing: Boolean = false,
	val jpegQuality: Byte = 95
) {
	enum class HardwareLevel {
		LEGACY, LIMITED, FULL, LEVEL_3
	}
	
	enum class TemplateType {
		PREVIEW, STILL_CAPTURE, RECORD, ZERO_SHUTTER_LAG
	}
	
	class Builder {
		private var hardwareLevel: HardwareLevel = HardwareLevel.FULL
		private var captureRequestTemplate: TemplateType = TemplateType.PREVIEW
		private var controlAfMode: Int? = null
		private var controlAeMode: Int? = null
		private var noiseReductionMode: Int? = null
		private var edgeMode: Int? = null
		private var useReprocessing: Boolean = false
		private var jpegQuality: Byte = 95
		
		fun hardwareLevel(level: HardwareLevel) = apply { this.hardwareLevel = level }
		fun captureRequestTemplate(template: TemplateType) = apply { this.captureRequestTemplate = template }
		fun controlAfMode(mode: Int) = apply { this.controlAfMode = mode }
		fun controlAeMode(mode: Int) = apply { this.controlAeMode = mode }
		fun noiseReductionMode(mode: Int) = apply { this.noiseReductionMode = mode }
		fun edgeMode(mode: Int) = apply { this.edgeMode = mode }
		fun reprocessing(enable: Boolean) = apply { this.useReprocessing = enable }
		fun jpegQuality(quality: Byte) = apply {
			require(quality in 0..100) { "JPEG quality must be 0-100" }
			this.jpegQuality = quality
		}
		
		fun build() = Camera2Config(
			hardwareLevel = hardwareLevel,
			captureRequestTemplate = captureRequestTemplate,
			controlAfMode = controlAfMode,
			controlAeMode = controlAeMode,
			noiseReductionMode = noiseReductionMode,
			edgeMode = edgeMode,
			useReprocessing = useReprocessing,
			jpegQuality = jpegQuality
		)
	}
}