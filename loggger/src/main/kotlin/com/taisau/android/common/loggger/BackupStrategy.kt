@file:Suppress("unused")

package com.taisau.android.common.loggger

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 日志文件备份策略 —— 在 [CleanupStrategy] 删除文件前先执行备份。
 *
 * 配合 [FilePlant] / [LogFileWriter] 使用，在清除旧日志前将文件
 * 复制或压缩到备份目录：
 *
 * ```
 * // 复制到备份目录
 * Loggger.plant(FilePlant(context,
 *     backupStrategy = BackupStrategy.copyTo(File(context.filesDir, "log_backup"))
 * ))
 *
 * // 压缩为 zip 到备份目录（最多保留 10 个 zip）
 * Loggger.plant(FilePlant(context,
 *     cleanupStrategy = CleanupStrategy.byAge(days = 7),
 *     backupStrategy = BackupStrategy.zipTo(File(context.filesDir, "log_archives"))
 * ))
 * ```
 */
fun interface BackupStrategy {

    /**
     * 备份即将被清理的日志文件。
     *
     * **注意**：此方法在清除锁内调用，应尽快返回。
     * 如果耗时较长（如大文件 zip），建议内部异步处理。
     *
     * @param files 即将被删除的文件列表（由 [CleanupStrategy] 选出）
     * @param logDir 日志文件当前所在目录
     */
    fun backup(files: List<File>, logDir: File)

    companion object {

        /** 不备份（默认）。文件被清除策略选中后直接删除。 */
        @JvmStatic
        val NO_BACKUP: BackupStrategy = BackupStrategy { _, _ -> }

        /**
         * 复制到备份目录 —— 将文件原样复制到 [backupDir]。
         *
         * 如果 [backupDir] 中已存在同名文件，自动追加时间后缀。
         *
         * @param backupDir 备份文件存放目录（自动创建）
         */
        @JvmStatic
        fun copyTo(backupDir: File): BackupStrategy = BackupStrategy { files, _ ->
            if (!backupDir.exists()) backupDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            for (src in files) {
                try {
                    val name = src.nameWithoutExtension
                    val ext = src.extension
                    val dest = File(backupDir, "${name}_backup_$timestamp.$ext")
                    src.copyTo(dest, overwrite = false)
                } catch (_: Exception) { }
            }
        }

        /**
         * 压缩为 zip 到备份目录 —— 将文件打包为一个 zip 归档。
         *
         * 每个归档文件名：`loggger_backup_20240707_103000.zip`
         *
         * @param backupDir zip 归档存放目录（自动创建）
         * @param maxBackupCount 保留的最大 zip 文件数（默认 10），
         *   超过时删除最旧的归档
         */
        @JvmStatic
        fun zipTo(
            backupDir: File,
            maxBackupCount: Int = 10
        ): BackupStrategy = BackupStrategy { files, _ ->
            if (files.isEmpty()) return@BackupStrategy
            if (!backupDir.exists()) backupDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(backupDir, "loggger_backup_$timestamp.zip")

            try {
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    for (src in files) {
                        if (!src.exists()) continue
                        zos.putNextEntry(ZipEntry(src.name))
                        FileInputStream(src).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

                // 清理旧归档
                val archives = backupDir.listFiles { f ->
                    f.name.startsWith("loggger_backup_") && f.name.endsWith(".zip")
                }?.sortedByDescending { it.lastModified() } ?: return@BackupStrategy

                if (archives.size > maxBackupCount) {
                    archives.drop(maxBackupCount).forEach { it.delete() }
                }
            } catch (_: Exception) { }
        }
    }
}
