package com.taisau.android.common.serialport

import androidx.annotation.IntDef

/**
 * 停止位约束注解。
 */
@IntDef(StopBits.ONE, StopBits.TWO)
@Retention(AnnotationRetention.SOURCE)
annotation class StopBits {
    companion object {
        /** 1 位停止位。 */
        const val ONE = 1

        /** 2 位停止位。 */
        const val TWO = 2
    }
}
