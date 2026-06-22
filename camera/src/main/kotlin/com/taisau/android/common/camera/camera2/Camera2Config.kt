package com.taisau.android.common.camera.camera2

/**
 * Camera2相机配置数据类，用于封装Camera API 2的各项高级配置参数。
 *
 * 该类采用不可变数据对象设计，所有配置项都有默认值，支持选择性配置。
 * Camera2 API提供了更精细的相机控制能力，包括硬件级别、请求模板、图像处理等。
 *
 * @property hardwareLevel 相机硬件支持级别，默认为FULL级别，决定设备支持的相机功能范围
 * @property captureRequestTemplate 捕获请求模板类型，默认为PREVIEW，定义相机的基础工作模式
 * @property controlAfMode 自动对焦模式控制，使用CameraMetadata.CONTROL_AF_MODE_*常量，null表示使用默认值
 * @property controlAeMode 自动曝光模式控制，使用CameraMetadata.CONTROL_AE_MODE_*常量，null表示使用默认值
 * @property noiseReductionMode 降噪处理模式，使用CameraMetadata.NOISE_REDUCTION_MODE_*常量，null表示使用默认值
 * @property edgeMode 边缘增强模式（锐化），使用CameraMetadata.EDGE_MODE_*常量，null表示使用默认值
 * @property useReprocessing 是否启用图像后处理功能，默认为false，启用后可对已捕获的图像进行再次处理
 * @property jpegQuality JPEG图片质量，范围0-100，默认为95，值越高质量越好但文件越大
 */
data class Camera2Config(
	val hardwareLevel: HardwareLevel = HardwareLevel.FULL,
	val captureRequestTemplate: TemplateType = TemplateType.PREVIEW,
	val controlAfMode: Int? = null,
	val controlAeMode: Int? = null,
	val noiseReductionMode: Int? = null,
	val edgeMode: Int? = null,
	val useReprocessing: Boolean = false,
	val jpegQuality: Int = 95
) {
	/**
	 * 相机硬件级别枚举
	 *
	 * 定义设备Camera2 API支持的硬件功能级别，级别越高支持的 advanced 功能越多。
	 * 从LEGACY到LEVEL_3，功能逐渐增强，但并非所有设备都支持最高级别。
	 */
	enum class HardwareLevel {
		LEGACY, LIMITED, FULL, LEVEL_3
	}
	
	/**
	 * 捕获请求模板类型枚举
	 *
	 * 预定义的相机请求模板，针对不同使用场景优化了各种相机参数。
	 * 每个模板都预设了适合特定场景的对焦、曝光、白平衡等参数组合。
	 */
	enum class TemplateType {
		PREVIEW, STILL_CAPTURE, RECORD, ZERO_SHUTTER_LAG
	}
	
	/**
	 * Camera2Config构建器类，提供流式API来逐步配置相机参数。
	 *
	 * 使用Builder模式可以避免构造函数参数过多，提高代码可读性。
	 * 所有配置项都是可选的，未设置的项将使用默认值。
	 * 支持链式调用，可以连续设置多个参数。
	 */
	class Builder {
		private var hardwareLevel: HardwareLevel = HardwareLevel.FULL
		private var captureRequestTemplate: TemplateType = TemplateType.PREVIEW
		private var controlAfMode: Int? = null
		private var controlAeMode: Int? = null
		private var noiseReductionMode: Int? = null
		private var edgeMode: Int? = null
		private var useReprocessing: Boolean = false
		private var jpegQuality: Int = 95
		
		/**
		 * 设置相机硬件级别
		 *
		 * 指定应用需要的最低硬件支持级别。如果设备不支持该级别，
		 * 可能在运行时降级或使用兼容模式。
		 *
		 * @param level 硬件级别枚举值
		 * @return Builder实例，支持链式调用
		 */
		fun hardwareLevel(level: HardwareLevel) = apply { this.hardwareLevel = level }
		
		/**
		 * 设置捕获请求模板类型
		 *
		 * 选择不同的模板会直接影响相机的行为模式，例如预览模式注重流畅度，
		 * 拍照模式注重画质，录像模式注重稳定性。
		 *
		 * @param template 模板类型枚举值
		 * @return Builder实例，支持链式调用
		 */
		fun captureRequestTemplate(template: TemplateType) = apply { this.captureRequestTemplate = template }
		
		/**
		 * 设置自动对焦模式
		 *
		 * 使用CameraMetadata中定义的CONTROL_AF_MODE_*常量。
		 * 常见值包括OFF、AUTO、MACRO、CONTINUOUS_VIDEO、CONTINUOUS_PICTURE等。
		 *
		 * @param mode 自动对焦模式常量值
		 * @return Builder实例，支持链式调用
		 */
		fun controlAfMode(mode: Int) = apply { this.controlAfMode = mode }
		
		/**
		 * 设置自动曝光模式
		 *
		 * 使用CameraMetadata中定义的CONTROL_AE_MODE_*常量。
		 * 常见值包括OFF、ON、ON_AUTO_FLASH、ON_ALWAYS_FLASH等。
		 *
		 * @param mode 自动曝光模式常量值
		 * @return Builder实例，支持链式调用
		 */
		fun controlAeMode(mode: Int) = apply { this.controlAeMode = mode }
		
		/**
		 * 设置降噪处理模式
		 *
		 * 使用CameraMetadata中定义的NOISE_REDUCTION_MODE_*常量。
		 * 影响图像的信噪比和细节保留程度，高速连拍时可能需要降低降噪强度。
		 *
		 * @param mode 降噪模式常量值
		 * @return Builder实例，支持链式调用
		 */
		fun noiseReductionMode(mode: Int) = apply { this.noiseReductionMode = mode }
		
		/**
		 * 设置边缘增强模式（锐化）
		 *
		 * 使用CameraMetadata中定义的EDGE_MODE_*常量。
		 * 控制图像的锐化程度，影响照片的清晰度和自然度。
		 *
		 * @param mode 边缘增强模式常量值
		 * @return Builder实例，支持链式调用
		 */
		fun edgeMode(mode: Int) = apply { this.edgeMode = mode }
		
		/**
		 * 设置是否启用图像后处理功能
		 *
		 * 启用后可以对已捕获的图像进行再次处理，如重新应用不同的ISP设置。
		 * 这会消耗更多内存和处理时间，但提供更大的灵活性。
		 *
		 * @param enable true启用后处理，false禁用
		 * @return Builder实例，支持链式调用
		 */
		fun reprocessing(enable: Boolean) = apply { this.useReprocessing = enable }
		
		/**
		 * 设置JPEG图片质量
		 *
		 * @param quality 质量值，必须在0-100范围内，值越高质量越好但文件越大
		 * @return Builder实例，支持链式调用
		 * @throws IllegalArgumentException 当quality不在0-100范围内时抛出异常
		 */
		fun jpegQuality(quality: Int) = apply {
			require(quality in 0..100) { "JPEG quality must be 0-100" }
			this.jpegQuality = quality
		}
		
		/**
		 * 构建并返回Camera2Config实例
		 *
		 * 使用当前Builder中配置的所有参数创建Camera2Config对象。
		 * 所有未显式设置的参数将使用默认值。
		 *
		 * @return 配置完成的Camera2Config实例
		 */
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