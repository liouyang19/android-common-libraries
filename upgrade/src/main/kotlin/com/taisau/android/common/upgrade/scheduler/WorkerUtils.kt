package com.taisau.android.common.upgrade.scheduler

import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 将 Guava [ListenableFuture] 转换为协程挂起函数。
 *
 * 用于等待 WorkManager 的 [ListenableFuture] 结果（如 [androidx.work.WorkManager.getWorkInfoById]）。
 */
internal suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: java.util.concurrent.ExecutionException) {
                    cont.resumeWithException(e.cause ?: e)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            Executor { r -> r.run() },
        )
        cont.invokeOnCancellation { cancel(false) }
    }
