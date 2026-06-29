package com.taisau.android.common.serialport

import androidx.annotation.IntDef

/**
 * 波特率约束注解。
 *
 * 取值与底层 JNI C++ 支持的波特率保持一致。
 */
@IntDef(
    BaudRate.B0,
    BaudRate.B50,
    BaudRate.B75,
    BaudRate.B110,
    BaudRate.B134,
    BaudRate.B150,
    BaudRate.B200,
    BaudRate.B300,
    BaudRate.B600,
    BaudRate.B1200,
    BaudRate.B1800,
    BaudRate.B2400,
    BaudRate.B4800,
    BaudRate.B9600,
    BaudRate.B19200,
    BaudRate.B38400,
    BaudRate.B57600,
    BaudRate.B115200,
    BaudRate.B230400,
    BaudRate.B460800,
    BaudRate.B500000,
    BaudRate.B576000,
    BaudRate.B921600,
    BaudRate.B1000000,
    BaudRate.B1152000,
    BaudRate.B1500000,
    BaudRate.B2000000,
    BaudRate.B2500000,
    BaudRate.B3000000,
    BaudRate.B3500000,
    BaudRate.B4000000
)
@Retention(AnnotationRetention.SOURCE)
annotation class BaudRate {
    companion object {
        const val B0 = 0
        const val B50 = 50
        const val B75 = 75
        const val B110 = 110
        const val B134 = 134
        const val B150 = 150
        const val B200 = 200
        const val B300 = 300
        const val B600 = 600
        const val B1200 = 1200
        const val B1800 = 1800
        const val B2400 = 2400
        const val B4800 = 4800
        const val B9600 = 9600
        const val B19200 = 19200
        const val B38400 = 38400
        const val B57600 = 57600
        const val B115200 = 115200
        const val B230400 = 230400
        const val B460800 = 460800
        const val B500000 = 500000
        const val B576000 = 576000
        const val B921600 = 921600
        const val B1000000 = 1000000
        const val B1152000 = 1152000
        const val B1500000 = 1500000
        const val B2000000 = 2000000
        const val B2500000 = 2500000
        const val B3000000 = 3000000
        const val B3500000 = 3500000
        const val B4000000 = 4000000
    }
}
