package com.taisau.android.common.upgrade.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * WorkManager 后台任务：安装 APK。
 *
 * 通过 [StrategyHolder] 获取 [InstallStrategy] 执行安装。
 *
 * 输入参数：
 * - [KEY_SAVE_PATH]：APK 文件的本地完整路径
 */
class InstallWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SAVE_PATH = "save_path"
        const val OUTPUT_SUCCESS = "install_success"
    }

    override suspend fun doWork(): Result {
        val savePath = inputData.getString(KEY_SAVE_PATH)
            ?: return Result.failure()

        val strategy = StrategyHolder.getInstall()
        strategy.install(savePath)

        return Result.success(workDataOf(OUTPUT_SUCCESS to true))
    }
}
