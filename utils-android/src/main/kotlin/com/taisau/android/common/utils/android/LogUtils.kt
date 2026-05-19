package com.taisau.android.common.utils.android

import android.util.Log

object LogUtils {

    var isDebug: Boolean = true
    var globalTag: String = "Utils"

    private fun buildTag(tag: String): String {
        return if (tag.isBlank()) globalTag else "$globalTag-$tag"
    }

    private fun buildMessage(message: String, vararg args: Any?): String {
        return if (args.isEmpty()) message else String.format(message, *args)
    }

    private fun getStackTrace(stackTrace: Throwable?): String {
        return stackTrace?.let { Log.getStackTraceString(it) } ?: ""
    }

    fun v(tag: String = "", message: String, vararg args: Any?) {
        if (isDebug) {
            Log.v(buildTag(tag), buildMessage(message, *args))
        }
    }

    fun d(tag: String = "", message: String, vararg args: Any?) {
        if (isDebug) {
            Log.d(buildTag(tag), buildMessage(message, *args))
        }
    }

    fun i(tag: String = "", message: String, vararg args: Any?) {
        Log.i(buildTag(tag), buildMessage(message, *args))
    }

    fun w(tag: String = "", message: String, vararg args: Any?) {
        Log.w(buildTag(tag), buildMessage(message, *args))
    }

    fun w(tag: String = "", throwable: Throwable) {
        Log.w(buildTag(tag), getStackTrace(throwable))
    }

    fun e(tag: String = "", message: String, vararg args: Any?) {
        Log.e(buildTag(tag), buildMessage(message, *args))
    }

    fun e(tag: String = "", throwable: Throwable) {
        Log.e(buildTag(tag), getStackTrace(throwable))
    }

    fun e(tag: String = "", message: String, throwable: Throwable) {
        Log.e(buildTag(tag), "$message\n${getStackTrace(throwable)}")
    }
}
