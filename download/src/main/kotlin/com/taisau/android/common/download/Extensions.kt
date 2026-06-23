package com.taisau.android.common.download

import android.content.Context

/**
 * [Context] 扩展属性 —— 获取或创建与 Application 关联的 [DownloadManager] 单例。
 *
 * 使用方式：
 * ```
 * val manager = context.downloadManager
 * val id = manager.download(url = "...", path = "...", fileName = "file.zip")
 * ```
 *
 * 单例由 [SingletonDownloadManager] 管理，创建策略：
 * 1. 如果通过 [SingletonDownloadManager.setSafe] 设置了 [SingletonDownloadManager.Factory]，使用之。
 * 2. 如果 [Application][android.app.Application] 实现了 [SingletonDownloadManager.Factory]，使用之。
 * 3. 否则使用 [DownloadManager.Builder] 默认配置自动创建。
 *
 * 后续重复调用会返回缓存的实例，不会重复创建。
 */
val Context.downloadManager: DownloadManager
    get() = SingletonDownloadManager.get(this)
