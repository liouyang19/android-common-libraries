package com.taisau.android.common.camera.camera1

/**
 * Camera1相机配置数据类，用于封装Camera API 1的各项配置参数。
 *
 * 该类采用不可变数据对象设计，所有配置项都有默认值，支持选择性配置。
 *
 * @property whiteBalance 白平衡模式，默认为自动白平衡（AUTO）
 * @property sceneMode 场景模式，默认为自动场景（AUTO）
 * @property pictureFormat 图片格式，默认为JPEG格式（android.graphics.ImageFormat.JPEG）
 * @property focusMode 对焦模式，默认为自动对焦（AUTO）
 * @property useSmoothZoom 是否启用平滑缩放功能，默认为false
 * @property jpegQuality JPEG图片质量，范围0-100，默认为95
 */
data class Camera1Config(
	val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
	val sceneMode: SceneMode = SceneMode.AUTO,
	val pictureFormat: Int = android.graphics.ImageFormat.JPEG,
	val focusMode: FocusMode = FocusMode.AUTO,
	val useSmoothZoom: Boolean = false,
	val jpegQuality: Int = 95
) {
	/**
	 * 白平衡模式枚举
	 * 
	 * 定义相机支持的白平衡设置选项，影响照片的色温表现。
	 */
	enum class WhiteBalance {
		AUTO, INCANDESCENT, FLUORESCENT, DAYLIGHT, CLOUDY_DAYLIGHT
	}
	
	/**
	 * 场景模式枚举
	 * 
	 * 定义相机支持的拍摄场景类型，相机会根据场景自动优化拍摄参数。
	 */
	enum class SceneMode {
		AUTO, PORTRAIT, LANDSCAPE, NIGHT, SPORTS
	}
	
	/**
	 * 对焦模式枚举
	 * 
	 * 定义相机支持的对焦方式，不同模式适用于不同的拍摄距离和对象。
	 */
	enum class FocusMode {
		AUTO, MACRO, INFINITY, FIXED, CONTINUOUS_PICTURE, CONTINUOUS_VIDEO
	}
	
	/**
	 * Camera1Config构建器类，提供流式API来逐步配置相机参数。
	 * 
	 * 使用Builder模式可以避免构造函数参数过多，提高代码可读性。
	 * 所有配置项都是可选的，未设置的项将使用默认值。
	 */
	class Builder {
		private var whiteBalance: WhiteBalance = WhiteBalance.AUTO
		private var sceneMode: SceneMode = SceneMode.AUTO
		private var pictureFormat: Int = android.graphics.ImageFormat.JPEG
		private var focusMode: FocusMode = FocusMode.AUTO
		private var useSmoothZoom: Boolean = false
		private var jpegQuality: Int = 95
		
		/**
		 * 设置白平衡模式
		 *
		 * @param wb 白平衡模式枚举值
		 * @return Builder实例，支持链式调用
		 */
		fun whiteBalance(wb: WhiteBalance) = apply { this.whiteBalance = wb }
		
		/**
		 * 设置场景模式
		 *
		 * @param mode 场景模式枚举值
		 * @return Builder实例，支持链式调用
		 */
		fun sceneMode(mode: SceneMode) = apply { this.sceneMode = mode }
		
		/**
		 * 设置图片格式
		 *
		 * @param format 图片格式常量，使用android.graphics.ImageFormat中定义的常量
		 * @return Builder实例，支持链式调用
		 */
		fun pictureFormat(format: Int) = apply { this.pictureFormat = format }
		
		/**
		 * 设置对焦模式
		 *
		 * @param mode 对焦模式枚举值
		 * @return Builder实例，支持链式调用
		 */
		fun focusMode(mode: FocusMode) = apply { this.focusMode = mode }
		
		/**
		 * 设置是否启用平滑缩放
		 *
		 * @param enable true启用平滑缩放，false禁用
		 * @return Builder实例，支持链式调用
		 */
		fun smoothZoom(enable: Boolean) = apply { this.useSmoothZoom = enable }
		
		/**
		 * 设置JPEG图片质量
		 *
		 * @param quality 质量值，必须在0-100 范围内，值越高质量越好但文件越大
		 * @return Builder实例，支持链式调用
		 * @throws IllegalArgumentException 当quality不在0-100范围内时抛出异常
		 */
		fun jpegQuality(quality: Int) = apply {
			require(quality in 0..100) { "JPEG quality must be 0-100" }
			this.jpegQuality = quality
		}
		
		/**
		 * 构建并返回Camera1Config实例
		 *
		 * 使用当前Builder中配置的所有参数创建Camera1Config对象。
		 *
		 * @return 配置完成的Camera1Config实例
		 */
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
