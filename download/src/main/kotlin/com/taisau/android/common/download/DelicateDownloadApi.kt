package com.taisau.android.common.download

/**
 * 标记不安全的下载 API —— 仅在测试或高级场景中使用。
 *
 * 被此注解标记的方法：
 * - 可能破坏单例状态
 * - 可能导致多个 [DownloadManager] 实例并存
 * - 仅供测试框架或明确知晓后果的开发者使用
 *
 * 使用时应使用 `@OptIn(DelicateDownloadApi::class)` 显式确认。
 */
@RequiresOptIn(
    message = "这是一个不安全的 API，仅应在测试或明确知晓后果的高级场景中使用。",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class DelicateDownloadApi
