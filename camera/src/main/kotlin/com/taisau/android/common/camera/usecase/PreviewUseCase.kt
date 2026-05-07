package com.taisau.android.common.camera.usecase

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import com.taisau.android.common.camera.core.ICamera
import com.taisau.android.common.camera.core.Resolution
import com.taisau.android.common.camera.core.UseCase
import kotlinx.coroutines.CompletableDeferred

class PreviewUseCase private constructor(
	val targetResolution: Resolution?,
	val targetAspectRatio: AspectRatio?,
	val targetRotation: Int?
) : UseCase() {
	
	enum class AspectRatio(val ratio: Float) {
		RATIO_4_3(4f / 3f),
		RATIO_16_9(16f / 9f),
		RATIO_1_1(1f)
	}
	
	private var surfaceProvider: Any? = null
	private var surface: Surface? = null
	private val surfaceDeferred = CompletableDeferred<Surface>()
	
	override val requiredSurfaces: List<Surface>
		get() = surface?.let { listOf(it) } ?: emptyList()
	
	fun setSurfaceProvider(provider: Any) {
		surfaceProvider = provider
		when (provider) {
			is SurfaceHolder -> {
				surface = provider.surface
				if (provider.surface?.isValid == true) {
					surfaceDeferred.complete(provider.surface)
				}
				provider.addCallback(object : SurfaceHolder.Callback {
					override fun surfaceCreated(holder: SurfaceHolder) {
						surface = holder.surface
						surfaceDeferred.complete(holder.surface)
					}
					override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
					override fun surfaceDestroyed(holder: SurfaceHolder) {
						surface = null
					}
				})
			}
			is TextureView -> {
				if (provider.isAvailable) {
					surface = Surface(provider.surfaceTexture)
					surfaceDeferred.complete(surface!!)
				}
				provider.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
					override fun onSurfaceTextureAvailable(
						surfaceTexture: SurfaceTexture,
						width: Int,
						height: Int
					) {
						surface = Surface(surfaceTexture)
						surfaceDeferred.complete(surface!!)
					}
					override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
					override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
						this@PreviewUseCase.surface = null
						return true
					}
					override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
				}
			}
			is SurfaceTexture -> {
				surface = Surface(provider)
				surfaceDeferred.complete(surface!!)
			}
			is Surface -> {
				surface = provider
				surfaceDeferred.complete(provider)
			}
		}
	}
	
	override suspend fun onCameraOpened(camera: ICamera) {
		if (surface == null && surfaceProvider != null) {
			surfaceDeferred.await()
		}
	}
	
	override suspend fun start() {
		super.start()
	}
	
	override suspend fun stop() {
		super.stop()
		surface = null
	}
	
	class Builder : UseCase.Builder<PreviewUseCase>() {
		var targetResolution: Resolution? = null
			private set
		var targetAspectRatio: AspectRatio? = null
			private set
		var targetRotation: Int? = null
			private set
		
		fun setTargetResolution(resolution: Resolution) = apply {
			this.targetResolution = resolution
		}
		fun setTargetResolution(width: Int, height: Int) = apply {
			this.targetResolution = Resolution(width, height)
		}
		fun setTargetAspectRatio(aspectRatio: AspectRatio) = apply { this.targetAspectRatio = aspectRatio }
		fun setTargetRotation(rotation: Int) = apply { this.targetRotation = rotation }
		
		override fun build(): PreviewUseCase {
			return PreviewUseCase(
				targetResolution = targetResolution,
				targetAspectRatio = targetAspectRatio,
				targetRotation = targetRotation
			)
		}
	}
}