package com.taisau.android.common.upgrade.strategy

/**
 * App 版本升级策略接口（总接口）。
 *
 * 继承 [CheckStrategy]、[DownloadStrategy]、[InstallStrategy] 三个子接口，
 * 覆盖版本检测、APK 下载、APK 安装的完整生命周期。
 *
 * 使用者可以直接实现此接口（同时实现全部三个子接口的方法），
 * 也可以分别实现 [CheckStrategy]、[DownloadStrategy]、[InstallStrategy] 的任意组合，
 * 通过 [com.taisau.android.common.upgrade.UpgradeManager.Builder] 注入。
 *
 * @see DefaultUpgradeStrategy 默认实现（HttpURLConnection + DownloadManager + FileProvider）
 */
interface UpgradeStrategy : CheckStrategy, DownloadStrategy, InstallStrategy
