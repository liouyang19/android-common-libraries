package com.taisau.android.common.utils.android

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object FileUtils {

    fun getCacheDir(context: Context): File {
        return context.cacheDir
    }

    fun getFilesDir(context: Context): File {
        return context.filesDir
    }

    fun getExternalCacheDir(context: Context): File? {
        return context.externalCacheDir
    }

    fun getExternalFilesDir(context: Context, type: String? = null): File? {
        return context.getExternalFilesDir(type)
    }

    fun writeFile(file: File, content: String, append: Boolean = false): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun writeBytes(file: File, bytes: ByteArray): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun readFile(file: File): String? {
        return try {
            if (!file.exists()) return null
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            null
        }
    }

    fun readBytes(file: File): ByteArray? {
        return try {
            if (!file.exists()) return null
            file.readBytes()
        } catch (e: IOException) {
            null
        }
    }

    fun deleteFile(file: File): Boolean {
        return if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    fun getFileSize(file: File): Long {
        return if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            file.length()
        }
    }

    fun copyFile(src: File, dest: File, overwrite: Boolean = false): Boolean {
        if (!src.exists()) return false
        if (dest.exists() && !overwrite) return false
        return try {
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = overwrite)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun moveFile(src: File, dest: File, overwrite: Boolean = false): Boolean {
        if (!src.exists()) return false
        if (dest.exists() && !overwrite) return false
        if (src.renameTo(dest)) return true
        return try {
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = overwrite)
            src.delete()
            true
        } catch (e: IOException) {
            false
        }
    }

    fun getFileName(path: String): String {
        return File(path).name
    }

    fun getFileExtension(path: String): String {
        val name = File(path).name
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex == -1) "" else name.substring(dotIndex + 1)
    }

    fun createTempFile(prefix: String, suffix: String, directory: File = File(System.getProperty("java.io.tmpdir"))): File? {
        return try {
            File.createTempFile(prefix, suffix, directory)
        } catch (e: IOException) {
            null
        }
    }
}
