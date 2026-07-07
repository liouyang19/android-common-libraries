@file:Suppress("unused")

package com.taisau.android.common.loggger

import android.content.Context
import java.io.File

/**
 * 文件保存 Plant —— 将日志写入文件，支持按大小旋转、清除策略和备份策略。
 *
 * ### 基础用法
 * ```
 * // 默认：cacheDir/loggger/，每文件 5MB，保留最新 3 个
 * Loggger.plant(FilePlant(context))
 * ```
 *
 * ### 自定义清除策略
 * ```
 * // 按数量保留 5 个
 * Loggger.plant(FilePlant(context, cleanupStrategy = CleanupStrategy.byCount(5)))
 *
 * // 按天数保留 7 天
 * Loggger.plant(FilePlant(context, cleanupStrategy = CleanupStrategy.byAge(days = 7)))
 *
 * // 按总大小上限 50MB
 * Loggger.plant(FilePlant(context, cleanupStrategy = CleanupStrategy.byTotalSize(50 * 1024 * 1024)))
 *
 * // 自定义：只保留包含 "ERROR" 的
 * Loggger.plant(FilePlant(context, cleanupStrategy = CleanupStrategy { files ->
 *     files.filter { !it.name.contains("ERROR") }
 * }))
 * ```
 *
 * ### 备份策略（清除前先备份）
 * ```
 * // 复制到 filesDir/log_backup/
 * Loggger.plant(FilePlant(context,
 *     cleanupStrategy = CleanupStrategy.byAge(days = 7),
 *     backupStrategy = BackupStrategy.copyTo(File(context.filesDir, "log_backup"))
 * ))
 *
 * // 压缩为 zip 到 filesDir/log_archives/（保留最近 10 个 zip）
 * Loggger.plant(FilePlant(context,
 *     cleanupStrategy = CleanupStrategy.byCount(3),
 *     backupStrategy = BackupStrategy.zipTo(File(context.filesDir, "log_archives"))
 * ))
 * ```
 *
 * @param context Application Context（用于获取默认缓存目录）
 * @param logDir 日志文件目录（默认 [Context.cacheDir]/loggger/）
 * @param maxFileSize 单文件最大字节数（默认 5MB）
 * @param cleanupStrategy 文件清除策略（默认 [CleanupStrategy.DEFAULT]）
 * @param backupStrategy 文件备份策略（默认 [BackupStrategy.NO_BACKUP]）
 * @param minLevel 最低日志级别（默认 [LogLevel.VERBOSE]）
 */
class FilePlant(
    context: Context,
    logDir: File? = null,
    maxFileSize: Long = 5 * 1024 * 1024,
    private val cleanupStrategy: CleanupStrategy = CleanupStrategy.DEFAULT,
    private val backupStrategy: BackupStrategy = BackupStrategy.NO_BACKUP,
    minLevel: LogLevel = LogLevel.VERBOSE
) : LogPlant() {

    init {
        this.minLevel = minLevel
    }

    private val writer = LogFileWriter(
        logDir = logDir ?: File(context.applicationContext.cacheDir, "loggger"),
        maxFileSize = maxFileSize,
        cleanupStrategy = cleanupStrategy,
        backupStrategy = backupStrategy
    )

    override fun performLog(entry: LogEntry) {
        writer.writeEntry(entry)
    }
}
