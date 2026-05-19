package com.taisau.android.common.utils.jvm

object NumberUtils {

    fun parseInt(str: String, default: Int = 0): Int {
        return try {
            str.toInt()
        } catch (e: NumberFormatException) {
            default
        }
    }

    fun parseLong(str: String, default: Long = 0L): Long {
        return try {
            str.toLong()
        } catch (e: NumberFormatException) {
            default
        }
    }

    fun parseFloat(str: String, default: Float = 0f): Float {
        return try {
            str.toFloat()
        } catch (e: NumberFormatException) {
            default
        }
    }

    fun parseDouble(str: String, default: Double = 0.0): Double {
        return try {
            str.toDouble()
        } catch (e: NumberFormatException) {
            default
        }
    }

    fun isNumber(str: String): Boolean {
        return try {
            str.toDouble()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }

    fun clamp(value: Int, min: Int, max: Int): Int {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    fun clamp(value: Long, min: Long, max: Long): Long {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    fun clamp(value: Float, min: Float, max: Float): Float {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    fun clamp(value: Double, min: Double, max: Double): Double {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    fun inRange(value: Int, min: Int, max: Int): Boolean {
        return value in min..max
    }

    fun inRange(value: Long, min: Long, max: Long): Boolean {
        return value in min..max
    }

    fun inRange(value: Double, min: Double, max: Double): Boolean {
        return value >= min && value <= max
    }

    fun formatNumber(value: Number, decimals: Int = 2): String {
        return String.format("%.${decimals}f", value.toDouble())
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    fun isEven(value: Int): Boolean = value % 2 == 0

    fun isOdd(value: Int): Boolean = value % 2 != 0

    fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }

    fun lcm(a: Int, b: Int): Int {
        return a / gcd(a, b) * b
    }

    fun factorial(n: Int): Long {
        if (n < 0) throw IllegalArgumentException("n must be non-negative")
        var result = 1L
        for (i in 2..n) result *= i
        return result
    }

    fun fib(n: Int): Long {
        if (n < 0) throw IllegalArgumentException("n must be non-negative")
        if (n <= 1) return n.toLong()
        var a = 0L
        var b = 1L
        for (i in 2..n) {
            val temp = a + b
            a = b
            b = temp
        }
        return b
    }

    fun toBinaryString(value: Int): String {
        return Integer.toBinaryString(value)
    }

    fun toHexString(value: Int): String {
        return Integer.toHexString(value)
    }

    fun toOctalString(value: Int): String {
        return Integer.toOctalString(value)
    }
}
