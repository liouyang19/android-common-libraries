package com.taisau.android.common.serialport

import androidx.annotation.IntDef

/**
 * 串口打开标志位约束注解。
 *
 * 透传给 `open()` 系统调用，支持按位或组合。
 */
@IntDef(
    flag = true,
    value = [
        OpenFlags.O_RDONLY,
        OpenFlags.O_WRONLY,
        OpenFlags.O_RDWR,
        OpenFlags.O_NONBLOCK
    ]
)
@Retention(AnnotationRetention.SOURCE)
annotation class OpenFlags {
    companion object {
        /** 只读。 */
        const val O_RDONLY = 0x00000000

        /** 只写。 */
        const val O_WRONLY = 0x00000001

        /** 读写。 */
        const val O_RDWR = 0x00000002

        /** 非阻塞模式。 */
        const val O_NONBLOCK = 0x00004000
    }
}
