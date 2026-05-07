package com.taisau.android.common.camera.usecase

import android.media.ImageReader
import android.view.Surface
import com.taisau.android.common.camera.camera1.Camera1Impl
import com.taisau.android.common.camera.camera2.Camera2Impl
import com.taisau.android.common.camera.core.ICamera
import com.taisau.android.common.camera.core.Resolution
import com.taisau.android.common.camera.core.UseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class ImageAnalysisUseCase private constructor(
	val targetResolution: Resolution,
	val backpressureStrategy: BackpressureStrategy,
	val imageQueueDepth: Int,
	val analysisMode: AnalysisMode,
	val analyzer: ImageAnalyzer?
) : UseCase() {
	
	enum class BackpressureStrategy {
		STRATEGY_KEEP_ONLY_LATEST,
		STRATEGY_BLOCK_PRODUCER,
		STRATEGY_DROP
	}
	
	enum class AnalysisMode {
		SINGLE_FRAME,
		STREAMING,
		BATCH
	}
	
	interface ImageAnalyzer {
		suspend fun analyze(frame: AnalysisResult.Frame): Any?
	}
	
	sealed class AnalysisResult {
		data class Frame(
			val data: ByteArray,
			val width: Int,
			val height: Int,
			val format: Int = 0,
			val timestamp: Long = System.currentTimeMillis()
		) : AnalysisResult() {
			override fun equals(other: Any?): Boolean {
				if (this === other) return true
				if (javaClass != other?.javaClass) return false
				
				other as Frame
				
				if (width != other.width) return false
				if (height != other.height) return false
				if (format != other.format) return false
				if (timestamp != other.timestamp) return false
				if (!data.contentEquals(other.data)) return false
				
				return true
			}
			
			override fun hashCode(): Int {
				var result = width
				result = 31 * result + height
				result = 31 * result + format
				result = 31 * result + timestamp.hashCode()
				result = 31 * result + data.contentHashCode()
				return result
			}
		}
		
		data class Error(val exception: Throwable) : AnalysisResult()
	}
	
	private var currentCamera: ICamera? = null
	private var imageReader: ImageReader? = null
	private var analysisSurface: Surface? = null
	private var analysisJob: Job? = null
	
	private val _analysisResults = MutableSharedFlow<AnalysisResult>(
		replay = 0,
		extraBufferCapacity = imageQueueDepth,
		onBufferOverflow = when (backpressureStrategy) {
			BackpressureStrategy.STRATEGY_KEEP_ONLY_LATEST ->
				kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
			BackpressureStrategy.STRATEGY_DROP ->
				kotlinx.coroutines.channels.BufferOverflow.DROP_LATEST
			BackpressureStrategy.STRATEGY_BLOCK_PRODUCER ->
				kotlinx.coroutines.channels.BufferOverflow.SUSPEND
		}
	)
	val analysisResults: SharedFlow<AnalysisResult> = _analysisResults
	
	override val requiredSurfaces: List<Surface>
		get() = analysisSurface?.let { listOf(it) } ?: emptyList()
	
	override suspend fun onCameraOpened(camera: ICamera) {
		currentCamera = camera
		
		when (camera) {
			is Camera2Impl -> {
				imageReader = ImageReader.newInstance(
					targetResolution.width,
					targetResolution.height,
					android.graphics.ImageFormat.YUV_420_888,
					imageQueueDepth
				)
				
				imageReader?.setOnImageAvailableListener({ reader ->
					when (analysisMode) {
						AnalysisMode.STREAMING -> processImage(reader.acquireLatestImage())
						AnalysisMode.SINGLE_FRAME -> {
							analysisJob?.cancel()
							analysisJob = CoroutineScope(Dispatchers.Default).launch {
								processImage(reader.acquireLatestImage())
							}
						}
						AnalysisMode.BATCH -> {
							var image = reader.acquireLatestImage()
							while (image != null) {
								processImage(image)
								image = reader.acquireNextImage()
							}
						}
					}
				}, null)
				
				analysisSurface = imageReader?.surface
			}
			is Camera1Impl -> {
				camera.setPreviewCallback { data, width, height ->
					CoroutineScope(Dispatchers.Default).launch {
						val frame = AnalysisResult.Frame(
							data = data,
							width = width,
							height = height,
							timestamp = System.currentTimeMillis()
						)
						_analysisResults.emit(frame)
						analyzer?.analyze(frame)
					}
				}
			}
		}
	}
	
	private fun processImage(image: android.media.Image?) {
		image ?: return
		try {
			val buffer = image.planes[0].buffer
			val data = ByteArray(buffer.remaining())
			buffer.get(data)
			
			CoroutineScope(Dispatchers.Default).launch {
				val frame = AnalysisResult.Frame(
					data = data,
					width = image.width,
					height = image.height,
					format = image.format,
					timestamp = image.timestamp
				)
				_analysisResults.emit(frame)
				analyzer?.analyze(frame)
			}
		} catch (e: Exception) {
			CoroutineScope(Dispatchers.Default).launch {
				_analysisResults.emit(AnalysisResult.Error(e))
			}
		} finally {
			image.close()
		}
	}
	
	override suspend fun start() {
		super.start()
	}
	
	override suspend fun stop() {
		super.stop()
		analysisJob?.cancel()
		imageReader?.close()
		imageReader = null
		analysisSurface = null
	}
	
	class Builder : UseCase.Builder<ImageAnalysisUseCase>() {
		var targetResolution: Resolution = Resolution(640, 480)
			private set
		var backpressureStrategy: BackpressureStrategy = BackpressureStrategy.STRATEGY_KEEP_ONLY_LATEST
			private set
		var imageQueueDepth: Int = 6
			private set
		var analysisMode: AnalysisMode = AnalysisMode.STREAMING
			private set
		var analyzer: ImageAnalyzer? = null
			private set
		
		fun setTargetResolution(resolution: Resolution) = apply { this.targetResolution = resolution }
		fun setTargetResolution(width: Int, height: Int) = apply {
			this.targetResolution = Resolution(width, height)
		}
		fun setBackpressureStrategy(strategy: BackpressureStrategy) = apply {
			this.backpressureStrategy = strategy
		}
		fun setImageQueueDepth(depth: Int) = apply {
			require(depth in 1..10) { "Queue depth must be 1-10" }
			this.imageQueueDepth = depth
		}
		fun setAnalysisMode(mode: AnalysisMode) = apply { this.analysisMode = mode }
		fun setAnalyzer(analyzer: ImageAnalyzer) = apply { this.analyzer = analyzer }
		
		override fun build(): ImageAnalysisUseCase {
			return ImageAnalysisUseCase(
				targetResolution = targetResolution,
				backpressureStrategy = backpressureStrategy,
				imageQueueDepth = imageQueueDepth,
				analysisMode = analysisMode,
				analyzer = analyzer
			)
		}
	}
}