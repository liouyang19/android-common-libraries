package com.taisau.android.common.upgrade.strategy

import com.taisau.android.common.upgrade.UpgradeInfo

/**
 * 版本检测策略接口。
 *
 * 定义如何从版本检查接口获取 [UpgradeInfo]。
 * [headers]、[timeout] 等配置应交给策略实现自行管理。
 *
 * @see DefaultCheckStrategy 默认实现（HttpURLConnection）
 */
interface CheckStrategy {

    /**
     * 检测新版本。
     *
     * @param checkUrl 版本检查接口 URL
     * @return [Result.Success] 包含 [UpgradeInfo]；[Result.Failure] 包含异常
     */
    suspend fun check(checkUrl: String): Result<UpgradeInfo>
}
