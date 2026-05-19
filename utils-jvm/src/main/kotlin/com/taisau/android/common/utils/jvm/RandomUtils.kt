package com.taisau.android.common.utils.jvm

import kotlin.random.Random

object RandomUtils {

    private val NUMBER_CHARS = "0123456789"
    private val ALPHA_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val ALPHA_NUMERIC_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:',.<>?/~`"

    fun randomInt(min: Int, max: Int): Int {
        require(min <= max) { "min must be <= max" }
        return Random.nextInt(min, max + 1)
    }

    fun randomLong(min: Long, max: Long): Long {
        require(min <= max) { "min must be <= max" }
        return Random.nextLong(min, max + 1)
    }

    fun randomFloat(min: Float = 0f, max: Float = 1f): Float {
        require(min <= max) { "min must be <= max" }
        return min + Random.nextFloat() * (max - min)
    }

    fun randomDouble(min: Double = 0.0, max: Double = 1.0): Double {
        require(min <= max) { "min must be <= max" }
        return min + Random.nextDouble() * (max - min)
    }

    fun randomBoolean(): Boolean {
        return Random.nextBoolean()
    }

    fun randomNumeric(length: Int): String {
        require(length > 0) { "length must be > 0" }
        return (1..length).map { NUMBER_CHARS[Random.nextInt(NUMBER_CHARS.length)] }.joinToString("")
    }

    fun randomAlpha(length: Int): String {
        require(length > 0) { "length must be > 0" }
        return (1..length).map { ALPHA_CHARS[Random.nextInt(ALPHA_CHARS.length)] }.joinToString("")
    }

    fun randomAlphaNumeric(length: Int): String {
        require(length > 0) { "length must be > 0" }
        return (1..length).map { ALPHA_NUMERIC_CHARS[Random.nextInt(ALPHA_NUMERIC_CHARS.length)] }.joinToString("")
    }

    fun randomString(length: Int, includeSpecial: Boolean = false): String {
        require(length > 0) { "length must be > 0" }
        val chars = if (includeSpecial) "$ALPHA_NUMERIC_CHARS$SPECIAL_CHARS" else ALPHA_NUMERIC_CHARS
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    fun randomElement(list: List<*>): Any? {
        if (list.isEmpty()) return null
        return list[Random.nextInt(list.size)]
    }

    fun randomElement(vararg elements: Any): Any {
        return elements[Random.nextInt(elements.size)]
    }

    fun <T> shuffle(list: MutableList<T>): List<T> {
        val copy = list.toMutableList()
        copy.shuffle()
        return copy
    }

    fun <T> sample(list: List<T>, count: Int): List<T> {
        require(count <= list.size) { "count must be <= list size" }
        return shuffle(list.toMutableList()).take(count)
    }

    fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        Random.nextBytes(bytes)
        return bytes
    }

    fun uuid(): String {
        return java.util.UUID.randomUUID().toString()
    }

    fun uuidShort(): String {
        return uuid().replace("-", "")
    }

    fun randomColor(): String {
        val r = randomInt(0, 255)
        val g = randomInt(0, 255)
        val b = randomInt(0, 255)
        return String.format("#%02x%02x%02x", r, g, b)
    }
}
