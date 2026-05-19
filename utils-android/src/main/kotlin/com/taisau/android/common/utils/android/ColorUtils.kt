package com.taisau.android.common.utils.android

import android.graphics.Color
import kotlin.math.abs

object ColorUtils {

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
    }

    fun rgb(red: Int, green: Int, blue: Int): Int {
        return Color.rgb(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
    }

    fun alpha(color: Int): Int = Color.alpha(color)

    fun red(color: Int): Int = Color.red(color)

    fun green(color: Int): Int = Color.green(color)

    fun blue(color: Int): Int = Color.blue(color)

    fun setAlpha(color: Int, alpha: Int): Int {
        return (alpha.coerceIn(0, 255) shl 24) or (color and 0x00ffffff)
    }

    fun parseColor(colorString: String): Int? {
        return try {
            Color.parseColor(colorString)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun toHex(color: Int, includeAlpha: Boolean = true): String {
        return if (includeAlpha) {
            String.format("#%08x", color)
        } else {
            String.format("#%06x", color and 0xffffff)
        }
    }

    fun fromHex(hex: String): Int? {
        return try {
            val cleaned = hex.trimStart('#')
            when (cleaned.length) {
                3 -> Color.rgb(
                    cleaned[0].toString().repeat(2).toInt(16),
                    cleaned[1].toString().repeat(2).toInt(16),
                    cleaned[2].toString().repeat(2).toInt(16)
                )
                6 -> Color.rgb(
                    cleaned.substring(0, 2).toInt(16),
                    cleaned.substring(2, 4).toInt(16),
                    cleaned.substring(4, 6).toInt(16)
                )
                8 -> Color.argb(
                    cleaned.substring(0, 2).toInt(16),
                    cleaned.substring(2, 4).toInt(16),
                    cleaned.substring(4, 6).toInt(16),
                    cleaned.substring(6, 8).toInt(16)
                )
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun brightness(color: Int): Float {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        return (0.299f * r + 0.587f * g + 0.114f * b)
    }

    fun luminance(color: Int): Float {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        val rs = if (r <= 0.03928f) r / 12.92f else Math.pow(((r + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        val gs = if (g <= 0.03928f) g / 12.92f else Math.pow(((g + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        val bs = if (b <= 0.03928f) b / 12.92f else Math.pow(((b + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * rs + 0.7152f * gs + 0.0722f * bs
    }

    fun isLight(color: Int): Boolean {
        return brightness(color) > 0.5f
    }

    fun isDark(color: Int): Boolean {
        return brightness(color) <= 0.5f
    }

    fun contrastTextColor(color: Int): Int {
        return if (isLight(color)) Color.BLACK else Color.WHITE
    }

    fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val r1 = red(color1)
        val g1 = green(color1)
        val b1 = blue(color1)
        val a1 = alpha(color1)
        val r2 = red(color2)
        val g2 = green(color2)
        val b2 = blue(color2)
        val a2 = alpha(color2)
        val t = ratio.coerceIn(0f, 1f)
        return argb(
            (a1 + (a2 - a1) * t).toInt(),
            (r1 + (r2 - r1) * t).toInt(),
            (g1 + (g2 - g1) * t).toInt(),
            (b1 + (b2 - b1) * t).toInt()
        )
    }

    fun darken(color: Int, factor: Float): Int {
        val f = (1f - factor.coerceIn(0f, 1f))
        return rgb(
            (red(color) * f).toInt(),
            (green(color) * f).toInt(),
            (blue(color) * f).toInt()
        )
    }

    fun lighten(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        return rgb(
            (red(color) + (255 - red(color)) * f).toInt(),
            (green(color) + (255 - green(color)) * f).toInt(),
            (blue(color) + (255 - blue(color)) * f).toInt()
        )
    }

    fun isColorSimilar(color1: Int, color2: Int, threshold: Int = 10): Boolean {
        return abs(red(color1) - red(color2)) <= threshold &&
                abs(green(color1) - green(color2)) <= threshold &&
                abs(blue(color1) - blue(color2)) <= threshold
    }

    fun randomColor(): Int {
        return rgb(
            (0..255).random(),
            (0..255).random(),
            (0..255).random()
        )
    }
}
