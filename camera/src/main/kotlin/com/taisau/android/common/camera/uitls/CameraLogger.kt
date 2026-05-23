package com.taisau.android.common.camera.uitls

import android.util.Log
enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

/**
 * 相机库日志输出接口，外部可实现此接口自定义日志处理方式（如写文件、上传等）
 */
interface CameraLogger {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}

/**
 * 默认日志实现，输出到 Logcat
 */
class DefaultCameraLogger : CameraLogger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.VERBOSE -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
            LogLevel.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
            LogLevel.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
            LogLevel.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
            LogLevel.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }
    }
}

object NoLogger : CameraLogger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
    }
}


object CameraLog {

    private const val DEFAULT_TAG = "CameraLib"

    var tag: String = DEFAULT_TAG
    var logger: CameraLogger = DefaultCameraLogger()

    fun v(message: String, tag: String = this.tag) {
        logger.log(LogLevel.VERBOSE, tag, message)
    }

    fun d(message: String, tag: String = this.tag) {
        logger.log(LogLevel.DEBUG, tag, message)
    }

    fun i(message: String, tag: String = this.tag) {
        logger.log(LogLevel.INFO, tag, message)
    }

    fun w(message: String, throwable: Throwable? = null, tag: String = this.tag) {
        logger.log(LogLevel.WARN, tag, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = this.tag) {
        logger.log(LogLevel.ERROR, tag, message, throwable)
    }
}