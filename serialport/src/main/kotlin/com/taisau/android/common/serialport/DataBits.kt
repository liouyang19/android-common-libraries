package com.taisau.android.common.serialport

import androidx.annotation.IntDef

/**
 * 数据位约束注解。
 */
@IntDef(DataBits.FIVE, DataBits.SIX, DataBits.SEVEN, DataBits.EIGHT)
@Retention(AnnotationRetention.SOURCE)
annotation class DataBits {
    companion object {
        const val FIVE = 5
        const val SIX = 6
        const val SEVEN = 7
        const val EIGHT = 8
    }
}
