package com.taisau.android.common.camera.core

import com.taisau.android.common.camera.core.ICamera
import android.view.Surface

abstract class UseCase {
	
	private var _isActive = false
	
	/** 该UseCase需要的Surface列表 */
	open val requiredSurfaces: List<Surface>
		get() = emptyList()
	
	/** 当相机打开时回调 */
	abstract suspend fun onCameraOpened(camera: ICamera)
	
	/** 启动UseCase */
	open suspend fun start() {
		_isActive = true
	}
	
	/** 停止UseCase */
	open suspend fun stop() {
		_isActive = false
	}
	
	/** 是否活跃 */
	fun isActive(): Boolean = _isActive
	
	/** Builder基类 */
	abstract class Builder<T : UseCase> {
		abstract fun build(): T
	}
	
}
