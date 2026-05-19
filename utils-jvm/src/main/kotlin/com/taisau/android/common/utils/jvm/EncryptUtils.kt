package com.taisau.android.common.utils.jvm

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptUtils {

    fun md5(input: String): String {
        return md5(input.toByteArray())
    }

    fun md5(input: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input).toHexString()
    }

    fun md5File(file: File): String? {
        return try {
            FileInputStream(file).use { md5Stream(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun md5Stream(stream: InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().toHexString()
    }

    fun sha1(input: String): String {
        return sha1(input.toByteArray())
    }

    fun sha1(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(input).toHexString()
    }

    fun sha256(input: String): String {
        return sha256(input.toByteArray())
    }

    fun sha256(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input).toHexString()
    }

    fun sha512(input: String): String {
        return sha512(input.toByteArray())
    }

    fun sha512(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-512")
        return digest.digest(input).toHexString()
    }

    fun base64Encode(input: String): String {
        return base64Encode(input.toByteArray())
    }

    fun base64Encode(input: ByteArray): String {
        return Base64.getEncoder().encodeToString(input)
    }

    fun base64Decode(input: String): ByteArray {
        return Base64.getDecoder().decode(input)
    }

    fun base64DecodeToString(input: String): String {
        return String(base64Decode(input), Charsets.UTF_8)
    }

    fun base64UrlEncode(input: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input)
    }

    fun base64UrlDecode(input: String): ByteArray {
        return Base64.getUrlDecoder().decode(input)
    }

    data class AesKey(
        val key: ByteArray,
        val iv: ByteArray
    )

    fun aesEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    fun aesDecrypt(encrypted: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(encrypted)
    }

    fun aesEncryptBase64(data: String, key: ByteArray, iv: ByteArray): String {
        return base64Encode(aesEncrypt(data.toByteArray(), key, iv))
    }

    fun aesDecryptBase64(encryptedBase64: String, key: ByteArray, iv: ByteArray): String {
        return String(aesDecrypt(base64Decode(encryptedBase64), key, iv), Charsets.UTF_8)
    }

    fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(key, "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data)
    }

    fun hmacSha256Hex(data: String, key: String): String {
        return hmacSha256(data.toByteArray(), key.toByteArray()).toHexString()
    }
}
