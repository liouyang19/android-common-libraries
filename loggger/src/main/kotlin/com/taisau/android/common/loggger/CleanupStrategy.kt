@file:Suppress("unused")

package com.taisau.android.common.loggger

import java.io.File

/**
 * 日志文件清除策略 —— 决定何时删除历史日志文件。
 *
 * 可选用内置策略或自定义 lambda：
 * ```
 * // 按数量保留
 * CleanupStrategy.byCount(5)
 *
 * // 按保留天数
 * CleanupStrategy.byAge(days = 7)
 *
 * // 按总大小上限
 * CleanupStrategy.byTotalSize(maxBytes = 50 * 1024 * 1024)
 *
 * // 自定义
 * CleanupStrategy { files -> files.filter { it.name.contains("DEBUG") } }
 * ```
 *
 * @see FilePlant
 * @see LogFileWriter
 */
fun interface CleanupStrategy {

    /**
     * 从给定文件列表中选择需要删除的文件。
     *
     * @param logFiles 目录中所有 `loggger_*.log` 文件，
     *   按最后修改时间 **降序**（最新的在前）
     * @return 需要删除的文件列表
     */
    fun selectFilesToDelete(logFiles: List<File>): List<File>

    companion object {

        /** 默认策略：保留最新 3 个文件。 */
        @JvmStatic
        val DEFAULT: CleanupStrategy = byCount(3)

        /** 从不清理（保留全部文件）。 */
        @JvmStatic
        val NO_CLEANUP: CleanupStrategy = CleanupStrategy { emptyList() }

        /**
         * 按文件数量保留 —— 保留最新的 [maxCount] 个文件，删除其余。
         *
         * @param maxCount 保留的最大文件数（必须 > 0）
         */
        @JvmStatic
        fun byCount(maxCount: Int): CleanupStrategy {
            require(maxCount > 0) { "maxCount must be > 0, got $maxCount" }
            return CleanupStrategy { files ->
                if (files.size > maxCount) files.drop(maxCount) else emptyList()
            }
        }

        /**
         * 按保留天数 —— 删除 N 天前的文件。
         *
         * @param days 保留天数（必须 > 0）。7 = 保留最近一周
         * @param referenceTime 参考时间戳（默认当前时间）
         */
        @JvmStatic
        fun byAge(days: Int, referenceTime: Long = System.currentTimeMillis()): CleanupStrategy {
            require(days > 0) { "days must be > 0, got $days" }
            val cutoff = referenceTime - days * 24L * 60 * 60 * 1000
            return CleanupStrategy { files ->
                files.filter { it.lastModified() < cutoff }
            }
        }

        /**
         * 按总大小上限 —— 日志总大小超过 [maxBytes] 时，
         * 从最旧的文件开始删除，直到总大小 ≤ [maxBytes]。
         *
         * @param maxBytes 日志总大小上限（必须 > 0）
         */
        @JvmStatic
        fun byTotalSize(maxBytes: Long): CleanupStrategy {
            require(maxBytes > 0) { "maxBytes must be > 0, got $maxBytes" }
            return CleanupStrategy { files ->
                // files 已按最新在前排序，reversed() 得到最旧在前
                val reversed = files.reversed()
                val total = reversed.sumOf { it.length() }
                if (total <= maxBytes) return@CleanupStrategy emptyList()

                val toDelete = mutableListOf<File>()
                var current = total
                for (f in reversed) {
                    if (current <= maxBytes) break
                    toDelete.add(f)
                    current -= f.length()
                }
                toDelete
            }
        }
    }
}
