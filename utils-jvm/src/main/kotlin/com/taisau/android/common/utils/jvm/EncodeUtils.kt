package com.taisau.android.common.utils.jvm

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object EncodeUtils {

    fun urlEncode(input: String, charset: String = "UTF-8"): String {
        return URLEncoder.encode(input, charset)
    }

    fun urlDecode(input: String, charset: String = "UTF-8"): String {
        return URLDecoder.decode(input, charset)
    }

    fun base64Encode(input: String): String {
        return java.util.Base64.getEncoder().encodeToString(input.toByteArray())
    }

    fun base64Encode(input: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(input)
    }

    fun base64Decode(input: String): ByteArray {
        return java.util.Base64.getDecoder().decode(input)
    }

    fun base64DecodeToString(input: String): String {
        return String(base64Decode(input), Charsets.UTF_8)
    }

    fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        try {
            GZIPOutputStream(bos).use { it.write(data) }
        } catch (e: IOException) {
            throw RuntimeException("GZIP compression failed", e)
        }
        return bos.toByteArray()
    }

    fun gzipDecompress(compressed: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        try {
            GZIPInputStream(compressed.inputStream()).use { gzip ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (gzip.read(buffer).also { bytesRead = it } != -1) {
                    bos.write(buffer, 0, bytesRead)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("GZIP decompression failed", e)
        }
        return bos.toByteArray()
    }

    fun htmlEscape(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    fun htmlUnescape(input: String): String {
        return input
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }

    fun xmlEscape(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun xmlUnescape(input: String): String {
        return input
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    fun unicodeEncode(input: String): String {
        val sb = StringBuilder()
        for (c in input) {
            sb.append(if (c.code in 0x20..0x7e) c else "\\u${String.format("%04x", c.code)}")
        }
        return sb.toString()
    }

    fun unicodeDecode(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            if (input[i] == '\\' && i + 5 < input.length && input[i + 1] == 'u') {
                val hex = input.substring(i + 2, i + 6)
                sb.append(hex.toInt(16).toChar())
                i += 6
            } else {
                sb.append(input[i])
                i++
            }
        }
        return sb.toString()
    }

    fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Hex string must have even length" }
        return ByteArray(len / 2) {
            ((Character.digit(hex[it * 2], 16) shl 4) + Character.digit(hex[it * 2 + 1], 16)).toByte()
        }
    }
}
