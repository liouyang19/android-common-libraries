package com.taisau.android.common.upgrade.strategy

import android.content.Context

/**
 * [UpgradeStrategy] 的完整默认实现。
 *
 * 通过 Kotlin 委托 ([by]) 组合 [DefaultCheckStrategy]、[DefaultDownloadStrategy]、
 * [DefaultInstallStrategy] 三个具体策略类，对外呈现为统一的 [UpgradeStrategy]。
 *
 * - [check] 使用 [HttpURLConnection] 发起 GET 请求
 * - [download] 使用系统 [DownloadManager]
 * - [install] 使用 [FileProvider] + [Intent.ACTION_VIEW]
 *
 * @param context Application Context
 * @param authority FileProvider authority，仅安装时使用
 */
class DefaultUpgradeStrategy(
    context: Context,
    authority: String = "${context.packageName}.fileprovider",
) : UpgradeStrategy,
    CheckStrategy by DefaultCheckStrategy(context),
    DownloadStrategy by DefaultDownloadStrategy(context),
    InstallStrategy by DefaultInstallStrategy(context, authority)
