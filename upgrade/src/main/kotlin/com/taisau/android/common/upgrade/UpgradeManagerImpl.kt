package com.taisau.android.common.upgrade

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.taisau.android.common.upgrade.scheduler.CheckWorker
import com.taisau.android.common.upgrade.scheduler.DownloadWorker
import com.taisau.android.common.upgrade.scheduler.InstallWorker
import com.taisau.android.common.upgrade.scheduler.StrategyHolder
import com.taisau.android.common.upgrade.scheduler.UpdateMode
import com.taisau.android.common.upgrade.scheduler.UpgradeScheduleConfig
import com.taisau.android.common.upgrade.scheduler.UpgradeScheduler
import com.taisau.android.common.upgrade.scheduler.await
import com.taisau.android.common.upgrade.strategy.DefaultCheckStrategy
import com.taisau.android.common.upgrade.strategy.DefaultDownloadStrategy
import com.taisau.android.common.upgrade.strategy.DefaultInstallStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * [UpgradeManager] 的默认实现。
 *
 * 内部维护 [MutableStateFlow] 跟踪升级状态，并通过 [state] 对外暴露。
 * 支持自动模式（[UpdateMode.AUTO_DOWNLOAD]）：检测到新版本后自动下载安装。
 *
 * 下载进度通过 [DownloadWorker] 内部轮询 [DownloadManager] + [setProgress] 上报，
 * [UpgradeManagerImpl] 通过 [WorkManager.getWorkInfoByIdFlow] 观察，无需额外轮询。
 *
 * ⚠️ 所有核心操作均为无参无返回的挂起函数，调用方应 collect [state] 驱动 UI。
 */
internal class UpgradeManagerImpl private constructor(
    private val context: Context,
    private val config: UpgradeManager.Config,
    private val scheduleConfig: UpgradeScheduleConfig?,
) : UpgradeManager {

    /** 内部协程作用域。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 内部可变状态。 */
    private val _state = MutableStateFlow<UpgradeState>(UpgradeState.Idle)

    /** 当前升级状态。调用方可以 collect 此 flow 驱动 UI。 */
    override val state: StateFlow<UpgradeState> = _state.asStateFlow()

    /** 当前 WorkManager Worker 的 ID，供 [cancel] 取消用。 */
    private var currentWorkId: UUID? = null

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "upgrade_manager"
        private const val NOTIFICATION_CHANNEL_NAME = "版本更新"
        private const val NOTIFICATION_ID_DOWNLOAD = 0x2002
        private const val NOTIFICATION_ID_INSTALL = 0x2003

        /**
         * 工厂方法，由 [UpgradeManager.Builder.build()] 调用。
         */
        fun create(context: Context, builder: UpgradeManager.Builder): UpgradeManagerImpl {
            val appContext = context.applicationContext
            val config = builder.toConfig()
            val authority = "${appContext.packageName}.fileprovider"
            val effectiveCheck = builder.getCheckStrategy() ?: DefaultCheckStrategy(appContext)
            val effectiveDownload = builder.getDownloadStrategy() ?: DefaultDownloadStrategy(appContext)
            val effectiveInstall = builder.getInstallStrategy()
                ?: DefaultInstallStrategy(appContext, authority)

            // 注册策略到 StrategyHolder，供 Workers 使用
            StrategyHolder.register(effectiveCheck, effectiveDownload, effectiveInstall)

            return UpgradeManagerImpl(
                context = appContext,
                config = config,
                scheduleConfig = config.scheduleConfig,
            )
        }
    }

    // ==================== 核心操作 ====================

    override suspend fun check() {
        if (config.checkUrl.isBlank()) {
            _state.value = UpgradeState.CheckFailed(
                IllegalStateException("checkUrl must be set")
            )
            return
        }

        _state.value = UpgradeState.Checking

        val inputData = Data.Builder()
            .putString(CheckWorker.KEY_CHECK_URL, config.checkUrl)
            .putBoolean(CheckWorker.KEY_NOTIFY_ENABLED, config.notifyEnabled)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<CheckWorker>()
            .setInputData(inputData)
            .addTag("upgrade_check_oneshot")
            .build()
        currentWorkId = workRequest.id

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest)

        try {
            val workInfo = workManager.getWorkInfoById(workRequest.id).await()
            if (workInfo == null) {
                _state.value = UpgradeState.CheckFailed(Exception("Check failed: null WorkInfo"))
                return
            }

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> onCheckSucceeded(workInfo.outputData)
                WorkInfo.State.FAILED -> {
                    _state.value = UpgradeState.CheckFailed(
                        Exception("Check failed: ${workInfo.outputData.getString("error")}")
                    )
                }
                else -> {
                    _state.value = UpgradeState.CheckFailed(
                        Exception("Check ${workInfo.state.name}: unexpected state")
                    )
                }
            }
        } catch (e: Exception) {
            _state.value = UpgradeState.CheckFailed(e)
        } finally {
            currentWorkId = null
        }
    }

    override suspend fun download() {
        // 从当前状态读取版本信息
        val info = when (val state = _state.value) {
            is UpgradeState.NewVersionReady -> state.info
            else -> {
                _state.value = UpgradeState.DownloadFailed(
                    IllegalStateException("No NewVersionReady state found. Call check() first.")
                )
                return
            }
        }

        // 生成 APK 存放路径
        val savePath = generateSavePath(info)
        _state.value = UpgradeState.Downloading(speed = 0f)

        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_DOWNLOAD_URL, info.downloadUrl)
            .putString(DownloadWorker.KEY_SAVE_PATH, savePath)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag("upgrade_download_oneshot")
            .build()
        currentWorkId = workRequest.id

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest)

        // 通过 WorkManager flow 观察下载进度，替代手动轮询
        try {
            workManager.getWorkInfoByIdFlow(workRequest.id)
                .filterNotNull()
                .first { workInfo ->
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            updateDownloadProgress(workInfo)
                            false // continue collecting
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            _state.value = UpgradeState.DownloadCompleted(savePath)
                            if (config.notifyEnabled) {
                                showDownloadNotification(savePath)
                            }
                            // AUTO_DOWNLOAD 模式：自动安装
                            if (scheduleConfig?.updateMode?.isAutoDownload == true) {
                                scope.launch { install() }
                            }
                            true // stop collecting
                        }
                        WorkInfo.State.FAILED -> {
                            _state.value = UpgradeState.DownloadFailed(
                                Exception("Download failed")
                            )
                            true // stop collecting
                        }
                        else -> false // ENQUEUED, BLOCKED, CANCELLED — keep waiting
                    }
                }
        } catch (e: Exception) {
            _state.value = UpgradeState.DownloadFailed(e)
        } finally {
            currentWorkId = null
        }
    }

    override suspend fun install() {
        // 从当前状态读取 APK 路径
        val savePath = when (val state = _state.value) {
            is UpgradeState.DownloadCompleted -> state.savePath
            else -> {
                _state.value = UpgradeState.InstallFailed(
                    IllegalStateException("No DownloadCompleted state found. Call download() first.")
                )
                return
            }
        }

        _state.value = UpgradeState.Installing

        val inputData = Data.Builder()
            .putString(InstallWorker.KEY_SAVE_PATH, savePath)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<InstallWorker>()
            .setInputData(inputData)
            .addTag("upgrade_install_oneshot")
            .build()
        currentWorkId = workRequest.id

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest)

        try {
            val workInfo = workManager.getWorkInfoById(workRequest.id).await()
            if (workInfo == null) {
                _state.value = UpgradeState.InstallFailed(Exception("Install failed: null WorkInfo"))
                return
            }

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    _state.value = UpgradeState.Installed
                    if (config.notifyEnabled) {
                        showInstallNotification()
                    }
                    // 延迟重置为 Idle，让调用方有机会观察到 Installed 状态
                    scope.launch {
                        delay(2000.milliseconds)
                        _state.value = UpgradeState.Idle
                    }
                }
                WorkInfo.State.FAILED -> {
                    _state.value = UpgradeState.InstallFailed(Exception("Install failed"))
                }
                else -> {
                    _state.value = UpgradeState.InstallFailed(
                        Exception("Install ${workInfo.state.name}: unexpected state")
                    )
                }
            }
        } catch (e: Exception) {
            _state.value = UpgradeState.InstallFailed(e)
        } finally {
            currentWorkId = null
        }
    }

    // ==================== 取消当前操作 ====================

    override fun cancel() {
        // 1. 取消 WorkManager Worker
        currentWorkId?.let { workId ->
            WorkManager.getInstance(context).cancelWorkById(workId)
            currentWorkId = null
        }

        // 2. 如果正在下载，取消 DownloadManager 的下载任务
        val currentState = _state.value
        if (currentState is UpgradeState.Downloading) {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                // 通过 DownloadWorker 的 progress 获取 downloadId 不再可能，
                // 取消 Worker 后 DownloadManager 中的任务会继续，但用户不再关心
            } catch (_: Exception) {
                // 忽略取消下载时的异常
            }
        }

        // 3. 重置状态
        _state.value = UpgradeState.Idle
    }

    // ==================== 定时调度 ====================

    override fun scheduleUpdateCheck() {
        val sc = scheduleConfig ?: return
        if (config.checkUrl.isBlank()) {
            throw IllegalStateException("checkUrl must be set before scheduling")
        }
        UpgradeScheduler(context).schedule(
            checkUrl = config.checkUrl,
            config = sc,
            notifyEnabled = config.scheduleNotifyEnabled,
            notifyTitle = config.scheduleNotifyTitle,
            notifyTextPrefix = config.scheduleNotifyTextPrefix,
        )
    }

    override fun cancelScheduledCheck() {
        UpgradeScheduler(context).cancel()
    }

    override fun isScheduledCheck(): Boolean {
        return UpgradeScheduler(context).isScheduled()
    }

    // ==================== 内部：check 成功处理 ====================

    private fun onCheckSucceeded(output: Data) {
        val hasNewVersion = output.getBoolean(CheckWorker.OUTPUT_HAS_NEW_VERSION, false)

        if (!hasNewVersion) {
            _state.value = UpgradeState.NoNewVersion
            return
        }

        val info = UpgradeInfo(
            hasNewVersion = true,
            versionCode = output.getLong(CheckWorker.OUTPUT_VERSION_CODE, 0L),
            versionName = output.getString(CheckWorker.OUTPUT_VERSION_NAME) ?: "",
            downloadUrl = output.getString(CheckWorker.OUTPUT_DOWNLOAD_URL) ?: "",
            changeLog = output.getString(CheckWorker.OUTPUT_CHANGE_LOG) ?: "",
            forceUpdate = output.getBoolean(CheckWorker.OUTPUT_FORCE_UPDATE, false),
            fileMd5 = output.getString(CheckWorker.OUTPUT_FILE_MD5)?.ifEmpty { null },
            fileSize = output.getLong(CheckWorker.OUTPUT_FILE_SIZE, 0L),
        )

        _state.value = UpgradeState.NewVersionReady(info)

        // AUTO_DOWNLOAD 模式：自动跳转到下载
        if (scheduleConfig?.updateMode?.isAutoDownload == true) {
            scope.launch { download() }
        }
    }

    // ==================== 内部：APK 存放路径 ====================

    /**
     * 生成 APK 下载的本地存放路径。
     *
     * 优先使用 [Config.saveDirectory]（用户指定的目录），
     * 未设置时自动使用 `cacheDir/upgrade/`。
     * 文件名格式：`update_<versionName>_<versionCode>.apk`
     */
    private fun generateSavePath(info: UpgradeInfo): String {
        val baseDir = config.saveDirectory?.let { File(it) }
            ?: File(context.cacheDir, "upgrade")
        baseDir.mkdirs()
        val fileName = "update_${info.versionName}_${info.versionCode}.apk"
        return File(baseDir, fileName).absolutePath
    }

    // ==================== 内部：下载进度更新（从 Worker setProgress 读取）====================

    /**
     * 从 [DownloadWorker] 上报的进度数据中解析实时进度，更新 [UpgradeState.Downloading]。
     */
    private fun updateDownloadProgress(workInfo: WorkInfo) {
        val progress = workInfo.progress
        val downloaded = progress.getLong(DownloadWorker.PROGRESS_BYTES_DOWNLOADED, 0L)
        val total = progress.getLong(DownloadWorker.PROGRESS_TOTAL_BYTES, 0L)
        val pct = if (total > 0) downloaded.toFloat() / total else 0f
        _state.value = UpgradeState.Downloading(
            progress = pct,
            bytesDownloaded = downloaded,
            totalBytes = total,
            speed = 0f,
        )
    }

    // ==================== 内部：通知栏通知 ====================

    /**
     * 确保通知渠道存在（Android 8.0+）。
     */
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "版本更新通知" }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * 构建系统通知的通用方法。
     */
    private fun buildNotification(
        title: String,
        text: String,
    ): NotificationCompat.Builder {
        ensureNotificationChannel()

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
    }

    private fun showDownloadNotification(savePath: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NOTIFICATION_ID_DOWNLOAD,
            buildNotification("下载完成", "APK 已下载到：$savePath").build(),
        )
    }

    private fun showInstallNotification() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NOTIFICATION_ID_INSTALL,
            buildNotification("安装完成", "APK 安装已完成").build(),
        )
    }
}

/** 判断当前 UpdateMode 是否为自动下载模式。 */
private val UpdateMode?.isAutoDownload: Boolean
    get() = this == UpdateMode.AUTO_DOWNLOAD
