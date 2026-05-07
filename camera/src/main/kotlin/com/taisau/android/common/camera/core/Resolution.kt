package com.taisau.android.common.camera.core

data class Resolution(
	val width: Int,
	val height: Int
){
	override fun toString(): String {
		return "Resolution(width=$width, height=$height)"
	}
	
	companion object {
		val SD = Resolution(640, 480)
		val HD = Resolution(1280, 720)
		val FHD = Resolution(1920, 1080)
		val UHD = Resolution(3840, 2160)
	}
}
