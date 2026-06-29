package com.taisau.android.common.serialport

import androidx.annotation.IntDef

/**
 * 校验位约束注解。
 */
@IntDef(Parity.NONE, Parity.ODD, Parity.EVEN)
@Retention(AnnotationRetention.SOURCE)
annotation class Parity {
    companion object {
        /** 无校验。 */
        const val NONE = 0

        /** 奇校验。 */
        const val ODD = 1

        /** 偶校验。 */
        const val EVEN = 2
    }
}
