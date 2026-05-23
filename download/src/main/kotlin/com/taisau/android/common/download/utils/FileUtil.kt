package com.taisau.android.common.download.utils

import java.io.File

/**
 * 文件工具方法。
 */
internal object FileUtil {

    /**
     * 删除文件及对应的临时文件。
     */
    fun deleteFileIfExists(path: String, name: String) {
        val file = File(path, name)
        if (file.exists()) file.delete()

        val tempFile = File(file.absolutePath + ".temp")
        if (tempFile.exists()) tempFile.delete()
    }

    /**
     * 如果文件名冲突，自动生成带序号的新文件名。
     * 例如 "file (1).zip", "file (2).zip"
     */
    fun resolveNamingConflicts(fileName: String, path: String): String {
        var newFileName = fileName
        var file = File(path, newFileName)
        var tempFile = File(file.absolutePath + ".temp")
        var counter = 1

        while (file.exists() || tempFile.exists()) {
            val name = fileName.substringBeforeLast(".")
            val extension = fileName.substringAfterLast(".")
            newFileName = "$name ($counter).$extension"
            file = File(path, newFileName)
            tempFile = File(file.absolutePath + ".temp")
            counter++
        }
        return newFileName
    }
}
