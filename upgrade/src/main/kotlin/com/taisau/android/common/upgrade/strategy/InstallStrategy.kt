package com.taisau.android.common.upgrade.strategy

/**
 * APK 安装策略接口。
 *
 * 定义如何安装指定路径下的 APK 文件。
 *
 * @see DefaultInstallStrategy 默认实现（FileProvider + Intent.ACTION_VIEW）
 */
interface InstallStrategy {

    /**
     * 安装指定路径的 APK。
     *
     * @param savePath APK 文件的本地完整路径
     */
    fun install(savePath: String)
}
