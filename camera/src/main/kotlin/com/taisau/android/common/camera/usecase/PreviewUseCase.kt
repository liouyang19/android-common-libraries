package com.taisau.android.common.camera.usecase

import android.view.Surface
import com.taisau.android.common.camera.core.ICamera
import com.taisau.android.common.camera.core.Resolution
import com.taisau.android.common.camera.core.UseCase

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

    private var surfaceProvider: SurfaceProvider? = null
    private var currentRequest: SurfaceRequest? = null
    private var surface: Surface? = null

    override val requiredSurfaces: List<Surface>
        get() = surface?.let { listOf(it) } ?: emptyList()

    fun setSurfaceProvider(provider: SurfaceProvider) {
        surfaceProvider = provider
    }

    override suspend fun onCameraOpened(camera: ICamera) {
        val resolution = targetResolution ?: Resolution.FHD
        val request = SurfaceRequest(resolution)
        currentRequest = request
        surfaceProvider?.onSurfaceRequested(request)
        surface = try {
            request.awaitSurface()
        } catch (e: Exception) {
            currentRequest = null
            null
        }
    }

    override suspend fun start() {
        super.start()
    }

    override suspend fun stop() {
        super.stop()
        currentRequest?.cancel()
        currentRequest = null
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
