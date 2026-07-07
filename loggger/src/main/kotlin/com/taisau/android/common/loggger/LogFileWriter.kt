package com.taisau.android.common.loggger

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志文件写入器 —— 将日志写入文件，支持按大小旋转、清除和备份策略。
 *
 * ### 文件命名规则
 * - 当前文件: `{dir}/loggger_2024-07-07.log`
 * - 旋转文件: `{dir}/loggger_2024-07-07.1.log`, `.2.log` ...
 *
 * ### 清除策略
 * 默认 [CleanupStrategy.DEFAULT]（保留最新 3 个文件），
 * 可按需替换为按天数、按总大小或自定义策略。
 *
 * ### 备份策略
 * 在清除前先执行备份（如复制到备份目录或压缩为 zip），
 * 默认 [BackupStrategy.NO_BACKUP]（不备份直接删除）。
 *
 * ### 线程安全
 * 所有写操作通过 [synchronized] 同步，支持多线程并发写入。
 *
 * @param logDir 日志文件存储目录
 * @param maxFileSize 单个文件最大字节数（默认 5MB）
 * @param cleanupStrategy 日志文件清除策略（默认 [CleanupStrategy.DEFAULT]）
 * @param backupStrategy 日志文件备份策略（默认 [BackupStrategy.NO_BACKUP]）
 */
class LogFileWriter(
    private val logDir: File,
    private val maxFileSize: Long = 5 * 1024 * 1024, // 5MB
    private val cleanupStrategy: CleanupStrategy = CleanupStrategy.DEFAULT,
    private val backupStrategy: BackupStrategy = BackupStrategy.NO_BACKUP
) {
    /** 当前正在写入的文件。 */
    private var currentFile: File? = null

    /** 当前文件输出流。 */
    private var writer: OutputStreamWriter? = null

    /** 当前文件的日期标签（yyyy-MM-dd），用于检测日期变更。 */
    private var currentDateTag: String? = null

    /** 日期格式化器（线程安全：每次使用前同步）。 */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val dateTagFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 最近一次清理时间（避免高频重复清理）。 */
    private var lastCleanTime: Long = 0L

    init {
        // 确保目录存在
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
    }

    /**
     * 写入一条日志到文件。
     *
     * @param level 日志级别
     * @param tag 日志标签
     * @param message 日志消息
     * @param throwable 异常堆栈（可选，将写入多行）
     */
    fun write(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: String? = null,
        processName: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val formatted = formatLogLine(level, tag, message, throwable, processName, timestamp)
        writeText(formatted)
    }

    /**
     * 写入一条预格式化的 [LogEntry] 到文件。
     */
    fun writeEntry(entry: LogEntry) {
        write(
            level = entry.level,
            tag = entry.tag,
            message = entry.message,
            throwable = entry.throwable,
            processName = entry.processName,
            timestamp = entry.timestamp
        )
    }

    /** 关闭当前文件流。 */
    fun close() {
        synchronized(this) {
            try {
                writer?.close()
            } catch (_: Exception) { }
            writer = null
            currentFile = null
            currentDateTag = null
        }
    }

    // ── 内部实现 ──

    private fun writeText(text: String) {
        synchronized(this) {
            try {
                ensureWriter()
                writer?.write(text)
                writer?.flush()
            } catch (_: Exception) {
                // 文件写入失败时静默降级
            }
        }
    }

    /**
     * 确保当前 writer 可用，必要时新建文件或旋转。
     */
    private fun ensureWriter() {
        val now = System.currentTimeMillis()
        val dateTag = dateTagFormat.format(Date(now))

        // 日期变更或首次写入 → 创建新文件
        if (dateTag != currentDateTag || writer == null) {
            close()
            currentDateTag = dateTag
            currentFile = createLogFile(dateTag)
            writer = OutputStreamWriter(
                FileOutputStream(currentFile, true),
                StandardCharsets.UTF_8
            )
        }

        // 检查文件大小 → 需要旋转
        val file = currentFile ?: return
        if (file.exists() && file.length() > maxFileSize) {
            rotate(file, dateTag)
            currentFile = createLogFile(dateTag)
            writer = OutputStreamWriter(
                FileOutputStream(currentFile, true),
                StandardCharsets.UTF_8
            )
        }

        // 定期清理旧文件（每 10 分钟一次）
        if (now - lastCleanTime > 10 * 60 * 1000L) {
            cleanOldFiles()
            lastCleanTime = now
        }
    }

    /**
     * 创建新日志文件。
     * 如果文件已存在则直接返回（追加模式）。
     */
    private fun createLogFile(dateTag: String): File {
        return File(logDir, "loggger_$dateTag.log").also {
            if (!it.exists()) {
                try {
                    it.createNewFile()
                } catch (_: Exception) { }
            }
        }
    }

    /**
     * 文件旋转：将当前文件重命名为带序号的后备文件。
     * 例如 loggger_2024-07-07.log → loggger_2024-07-07.1.log
     */
    private fun rotate(file: File, dateTag: String) {
        try {
            // 将所有旧文件序号 +1（从大到小，避免覆盖）
            var seq = 1
            while (File(logDir, "loggger_${dateTag}.$seq.log").exists()) {
                seq++
            }
            // 从后往前移动，避免覆盖
            for (i in seq downTo 1) {
                val src = if (i == 1) file else File(logDir, "loggger_${dateTag}.${i - 1}.log")
                val dst = File(logDir, "loggger_${dateTag}.$i.log")
                if (src.exists()) {
                    src.renameTo(dst)
                }
            }
            // 清空当前文件（实际已重命名，重新创建即可）
            currentFile = createLogFile(dateTag)
        } catch (_: Exception) { }
    }

    /**
     * 根据 [cleanupStrategy] 清理历史日志文件。
     *
     * 流程：策略选出待删文件 → [backupStrategy] 备份 → 删除。
     * 扫描目录中所有 `loggger_*.log` 文件，交由策略决定删除哪些。
     */
    private fun cleanOldFiles() {
        try {
            val allFiles = logDir.listFiles { f ->
                f.name.startsWith("loggger_") && f.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() } ?: return

            if (allFiles.isEmpty()) return
            val toDelete = cleanupStrategy.selectFilesToDelete(allFiles)
            if (toDelete.isEmpty()) return

            // 1) 备份（在删除前）
            backupStrategy.backup(toDelete, logDir)

            // 2) 删除
            for (f in toDelete) {
                try { f.delete() } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    companion object {
        /**
         * 将日志格式化为一行文本。
         * 格式：`2024-07-07 10:30:00.123 [DEBUG] [Tag] Message`
         */
        fun formatLogLine(
            level: LogLevel,
            tag: String,
            message: String,
            throwable: String? = null,
            processName: String? = null,
            timestamp: Long = System.currentTimeMillis()
        ): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            val time = sdf.format(Date(timestamp))
            val proc = if (processName != null) " [$processName]" else ""
            val sb = StringBuilder()
            sb.append("$time [$level]$proc [$tag] $message")
            if (throwable != null) {
                // 异常堆栈缩进展示
                throwable.lines().forEach { line ->
                    sb.append("\n    $line")
                }
            }
            sb.append("\n")
            return sb.toString()
        }
    }
}
