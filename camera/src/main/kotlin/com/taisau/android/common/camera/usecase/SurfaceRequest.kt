package com.taisau.android.common.camera.usecase

import android.view.Surface
import com.taisau.android.common.camera.core.Resolution
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * 相机 Surface 请求类，用于封装对指定分辨率 Surface 的异步请求。
 *
 * 该类协调 Surface 的提供者（Provider）和消费者（UseCase）之间的异步交互。
 * 消费者通过 [awaitSurface] 挂起等待 Surface，提供者通过 [provideSurface] 提供 Surface，
 * 或通过 [willNotProvideSurface] 拒绝提供。请求可在任意时刻被 [cancel] 取消。
 *
 * @property resolution 请求的 Surface 分辨率
 */
class SurfaceRequest(
    val resolution: Resolution
) {
    /**
     * Surface 提供操作的结果状态
     */
    enum class Result {
        /** Surface 已成功提供并被使用 */
        SURFACE_USED_SUCCESSFULLY,
        /** 请求已被取消，Surface 未被使用 */
        REQUEST_CANCELLED,
        /** 提供的 Surface 无效（已释放或未初始化） */
        INVALID_SURFACE,
        /** Surface 已经被提供过，不允许重复提供 */
        SURFACE_ALREADY_PROVIDED,
        /** 提供者明确拒绝提供 Surface */
        WILL_NOT_PROVIDE_SURFACE
    }

    private val deferred = CompletableDeferred<Surface>()
    private var providedSurface: Surface? = null
    private var isCancelled = false
    private var isWillNotProvide = false
    private val cancellationListeners = CopyOnWriteArrayList<Pair<Executor, Runnable>>()

    /**
     * 提供 Surface 以满足此请求。
     *
     * 此方法会依次校验：请求是否已取消、是否已拒绝提供、是否已提供过、Surface 是否有效。
     * 校验通过后，Surface 将被绑定到请求并通知等待方，同时通过回调返回成功结果。
     * 所有校验失败和成功回调均通过指定的 executor 异步执行。
     *
     * @param surface 要提供的 Surface 实例
     * @param executor 用于执行回调的线程执行器
     * @param callback 操作结果回调，接收 [Result] 枚举值
     */
    fun provideSurface(surface: Surface, executor: Executor, callback: (Result) -> Unit) {
        // 按优先级依次校验请求状态和 Surface 有效性
        if (isCancelled) {
            executor.execute { callback(Result.REQUEST_CANCELLED) }
            return
        }
        if (isWillNotProvide) {
            executor.execute { callback(Result.WILL_NOT_PROVIDE_SURFACE) }
            return
        }
        if (providedSurface != null) {
            executor.execute { callback(Result.SURFACE_ALREADY_PROVIDED) }
            return
        }
        if (!surface.isValid) {
            executor.execute { callback(Result.INVALID_SURFACE) }
            return
        }
        // 校验通过，绑定 Surface 并完成异步等待
        providedSurface = surface
        deferred.complete(surface)
        executor.execute { callback(Result.SURFACE_USED_SUCCESSFULLY) }
    }

    /**
     * 声明拒绝提供 Surface。
     *
     * 调用后，[awaitSurface] 的等待方将收到 [IllegalStateException] 异常。
     * 此操作不可逆，调用后 [provideSurface] 也将被拒绝。
     */
    fun willNotProvideSurface() {
        isWillNotProvide = true
        deferred.completeExceptionally(
            IllegalStateException("SurfaceProvider declined to provide a surface")
        )
    }

    /**
     * 添加请求取消监听器。
     *
     * 如果请求已被取消，监听器会立即通过 executor 执行；
     * 否则监听器将被注册，在 [cancel] 被调用时触发。
     *
     * @param executor 用于执行监听器的线程执行器
     * @param listener 取消时触发的回调
     */
    fun addRequestCancellationListener(executor: Executor, listener: Runnable) {
        if (isCancelled) {
            executor.execute(listener)
        } else {
            cancellationListeners.add(executor to listener)
        }
    }

    /**
     * 取消此 Surface 请求。
     *
     * 取消操作是幂等的，重复调用不会产生副作用。
     * 取消后会通过 CancellationException 通知 [awaitSurface] 的等待方，
     * 并依次触发所有已注册的取消监听器，最后清空监听器列表。
     */
    internal fun cancel() {
        if (isCancelled) return
        isCancelled = true
        deferred.completeExceptionally(
            java.util.concurrent.CancellationException("Surface request cancelled")
        )
        cancellationListeners.forEach { (executor, listener) ->
            executor.execute(listener)
        }
        cancellationListeners.clear()
    }

    /**
     * 挂起等待直到 Surface 被提供。
     *
     * 如果 [provideSurface] 成功调用，返回提供的 Surface；
     * 如果请求被取消或拒绝提供，将抛出对应异常。
     *
     * @return 提供的 Surface 实例
     * @throws java.util.concurrent.CancellationException 请求被取消时
     * @throws IllegalStateException 提供者拒绝提供 Surface 时
     */
    suspend fun awaitSurface(): Surface = deferred.await()
}

/**
 * Surface 提供者函数式接口。
 *
 * 当相机 UseCase 需要一个 Surface 时，会通过此接口回调通知外部，
 * 外部应在回调中调用 [SurfaceRequest.provideSurface] 提供 Surface，
 * 或调用 [SurfaceRequest.willNotProvideSurface] 拒绝提供。
 */
fun interface SurfaceProvider {
    /**
     * 当有新的 Surface 请求时回调
     *
     * @param request Surface 请求对象，包含所需分辨率等信息
     */
    fun onSurfaceRequested(request: SurfaceRequest)
}
