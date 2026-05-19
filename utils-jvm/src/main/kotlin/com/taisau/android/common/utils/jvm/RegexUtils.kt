package com.taisau.android.common.utils.jvm

object RegexUtils {

    private val REGEX_EMAIL = Regex("^[\\w\\-]+(\\.[\\w\\-]+)*@[\\w\\-]+(\\.[\\w\\-]+)+$")
    private val REGEX_PHONE = Regex("^1[3-9]\\d{9}$")
    private val REGEX_URL = Regex("^(https?|ftp|file)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]$")
    private val REGEX_IPV4 = Regex("^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")
    private val REGEX_ID_CARD = Regex("^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$")
    private val REGEX_CHINESE = Regex("^[\\u4e00-\\u9fff]+$")
    private val REGEX_DIGITS = Regex("^\\d+$")
    private val REGEX_DECIMAL = Regex("^-?\\d+(\\.\\d+)?$")
    private val REGEX_ALPHA = Regex("^[A-Za-z]+$")
    private val REGEX_ALPHA_NUMERIC = Regex("^[A-Za-z0-9]+$")
    private val REGEX_HEX_COLOR = Regex("^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

    fun isEmail(input: String): Boolean {
        return input.matches(REGEX_EMAIL)
    }

    fun isPhone(input: String): Boolean {
        return input.matches(REGEX_PHONE)
    }

    fun isUrl(input: String): Boolean {
        return input.matches(REGEX_URL)
    }

    fun isIpv4(input: String): Boolean {
        return input.matches(REGEX_IPV4)
    }

    fun isIdCard(input: String): Boolean {
        if (!input.matches(REGEX_ID_CARD)) return false
        val factors = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
        val checksum = charArrayOf('1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2')
        val sum = (0 until 17).sumOf { (input[it] - '0') * factors[it] }
        return checksum[sum % 11] == input[17].uppercaseChar()
    }

    fun isChinese(input: String): Boolean {
        return input.matches(REGEX_CHINESE)
    }

    fun isDigits(input: String): Boolean {
        return input.matches(REGEX_DIGITS)
    }

    fun isDecimal(input: String): Boolean {
        return input.matches(REGEX_DECIMAL)
    }

    fun isAlpha(input: String): Boolean {
        return input.matches(REGEX_ALPHA)
    }

    fun isAlphaNumeric(input: String): Boolean {
        return input.matches(REGEX_ALPHA_NUMERIC)
    }

    fun isHexColor(input: String): Boolean {
        return input.matches(REGEX_HEX_COLOR)
    }

    fun extractEmails(text: String): List<String> {
        return REGEX_EMAIL.findAll(text).map { it.value }.toList()
    }

    fun extractUrls(text: String): List<String> {
        return REGEX_URL.findAll(text).map { it.value }.toList()
    }

    fun extractDigits(text: String): List<String> {
        return REGEX_DIGITS.findAll(text).map { it.value }.toList()
    }

    fun replaceAll(text: String, regex: String, replacement: String): String {
        return text.replace(Regex(regex), replacement)
    }

    fun countMatches(text: String, regex: String): Int {
        return Regex(regex).findAll(text).count()
    }
}
