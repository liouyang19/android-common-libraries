package com.taisau.android.common.utils.jvm

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.charset.Charset

object FileUtilsJvm {

    private const val BUFFER_SIZE = 8192

    fun readText(file: File, charset: Charset = Charsets.UTF_8): String? {
        return try {
            if (!file.exists()) return null
            file.readText(charset)
        } catch (e: IOException) {
            null
        }
    }

    fun readLines(file: File, charset: Charset = Charsets.UTF_8): List<String>? {
        return try {
            if (!file.exists()) return null
            file.readLines(charset)
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

    fun writeText(file: File, text: String, charset: Charset = Charsets.UTF_8, append: Boolean = false): Boolean {
        return try {
            file.parentFile?.mkdirs()
            if (append) file.appendText(text, charset) else file.writeText(text, charset)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun writeBytes(file: File, bytes: ByteArray, append: Boolean = false): Boolean {
        return try {
            file.parentFile?.mkdirs()
            if (append) file.appendBytes(bytes) else file.writeBytes(bytes)
            true
        } catch (e: IOException) {
            false
        }
    }

    fun copy(src: File, dest: File, overwrite: Boolean = false): Boolean {
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

    fun copyStream(input: InputStream, output: OutputStream, closeStreams: Boolean = true): Boolean {
        return try {
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.flush()
            true
        } catch (e: IOException) {
            false
        } finally {
            if (closeStreams) {
                try { input.close() } catch (_: IOException) {}
                try { output.close() } catch (_: IOException) {}
            }
        }
    }

    fun copyFileWithChannel(src: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    val inChannel: FileChannel = input.channel
                    val outChannel: FileChannel = output.channel
                    inChannel.transferTo(0, inChannel.size(), outChannel)
                }
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    fun move(src: File, dest: File, overwrite: Boolean = false): Boolean {
        if (!src.exists()) return false
        if (dest.exists() && !overwrite) return false
        return try {
            dest.parentFile?.mkdirs()
            src.renameTo(dest) || (src.copyTo(dest, overwrite = overwrite).also { src.delete() } != null)
        } catch (e: IOException) {
            false
        }
    }

    fun delete(file: File): Boolean {
        return if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    fun getSize(file: File): Long {
        if (!file.exists()) return 0
        return if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            file.length()
        }
    }

    fun getExtension(path: String): String {
        val name = File(path).name
        val dot = name.lastIndexOf('.')
        return if (dot == -1) "" else name.substring(dot + 1)
    }

    fun getNameWithoutExtension(path: String): String {
        val name = File(path).name
        val dot = name.lastIndexOf('.')
        return if (dot == -1) name else name.substring(0, dot)
    }

    fun createTempFile(prefix: String, suffix: String, directory: File? = null): File? {
        return try {
            File.createTempFile(prefix, suffix, directory)
        } catch (e: IOException) {
            null
        }
    }

    fun createDir(dir: File): Boolean {
        return dir.mkdirs()
    }

    fun listFiles(dir: File, recursive: Boolean = false): List<File> {
        if (!dir.isDirectory) return emptyList()
        return if (recursive) {
            dir.walkTopDown().filter { it.isFile }.toList()
        } else {
            dir.listFiles()?.filter { it.isFile } ?: emptyList()
        }
    }

    fun findFiles(dir: File, extension: String, recursive: Boolean = true): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.extension == extension }.toList()
    }

    fun readFirstLine(file: File): String? {
        return try {
            if (!file.exists()) return null
            BufferedReader(FileReader(file)).use { it.readLine() }
        } catch (e: IOException) {
            null
        }
    }

    fun tail(file: File, lines: Int): List<String> {
        if (!file.exists()) return emptyList()
        return try {
            file.useLines { it.toList().takeLast(lines) }
        } catch (e: IOException) {
            emptyList()
        }
    }
}
