package com.taisau.android.common.download.utils

import android.util.Log

interface DownloadLogger {
    fun log(priority: LogPriority, tag: String, message: String, throwable: Throwable? = null)
    enum class LogPriority { VERBOSE, DEBUG, INFO, WARN, ERROR }
}

class DefaultDownloadLogger : DownloadLogger {
    override fun log(priority: DownloadLogger.LogPriority, tag: String, message: String, throwable: Throwable?) {
        val logPriority = when (priority) {
            DownloadLogger.LogPriority.VERBOSE -> Log.VERBOSE
            DownloadLogger.LogPriority.DEBUG -> Log.DEBUG
            DownloadLogger.LogPriority.INFO -> Log.INFO
            DownloadLogger.LogPriority.WARN -> Log.WARN
            DownloadLogger.LogPriority.ERROR -> Log.ERROR
        }
        if (throwable != null) Log.println(logPriority, tag, "$message\n${Log.getStackTraceString(throwable)}")
        else Log.println(logPriority, tag, message)
    }
}