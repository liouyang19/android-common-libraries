package com.taisau.android.common.utils.jvm

object StringUtils {

    fun isEmpty(str: CharSequence?): Boolean {
        return str.isNullOrEmpty()
    }

    fun isBlank(str: CharSequence?): Boolean {
        return str == null || str.toString().trim().isEmpty()
    }

    fun isNotEmpty(str: CharSequence?): Boolean {
        return !isEmpty(str)
    }

    fun isNotBlank(str: CharSequence?): Boolean {
        return !isBlank(str)
    }

    fun capitalize(str: String): String {
        return str.replaceFirstChar { it.uppercase() }
    }

    fun decapitalize(str: String): String {
        return str.replaceFirstChar { it.lowercase() }
    }

    fun reverse(str: String): String {
        return str.reversed()
    }

    fun repeat(str: String, count: Int): String {
        return str.repeat(count)
    }

    fun truncate(str: String, maxLength: Int, ellipsis: String = "..."): String {
        return if (str.length <= maxLength) str
        else str.take(maxLength - ellipsis.length) + ellipsis
    }

    fun countMatches(str: String, sub: String): Int {
        if (sub.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            idx = str.indexOf(sub, idx)
            if (idx < 0) break
            count++
            idx += sub.length
        }
        return count
    }

    fun removePrefix(str: String, prefix: String): String {
        return if (str.startsWith(prefix)) str.removePrefix(prefix) else str
    }

    fun removeSuffix(str: String, suffix: String): String {
        return if (str.endsWith(suffix)) str.removeSuffix(suffix) else str
    }

    fun defaultIfBlank(str: String?, default: String): String {
        return if (isBlank(str)) default else str!!
    }

    fun defaultIfEmpty(str: String?, default: String): String {
        return if (isEmpty(str)) default else str!!
    }

    fun toCamelCase(str: String, delimiter: Char = '_'): String {
        return str.split(delimiter).joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
            .replaceFirstChar { it.lowercase() }
    }

    fun toSnakeCase(str: String): String {
        return str.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
            .lowercase()
    }

    fun difference(str1: String, str2: String): String {
        val minLen = minOf(str1.length, str2.length)
        val diffIdx = (0 until minLen).firstOrNull { str1[it] != str2[it] } ?: return ""
        return str2.substring(diffIdx)
    }

    fun levenshteinDistance(str1: String, str2: String): Int {
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }
        for (i in 0..str1.length) dp[i][0] = i
        for (j in 0..str2.length) dp[0][j] = j
        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[str1.length][str2.length]
    }

    fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    fun String.hexToBytes(): ByteArray {
        val len = length
        require(len % 2 == 0) { "Hex string must have even length" }
        return ByteArray(len / 2) {
            ((Character.digit(this[it * 2], 16) shl 4) + Character.digit(this[it * 2 + 1], 16)).toByte()
        }
    }
}
