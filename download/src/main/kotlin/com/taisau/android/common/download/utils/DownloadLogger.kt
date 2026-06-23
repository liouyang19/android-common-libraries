package com.taisau.android.common.download.utils

import android.util.Log
import com.taisau.android.common.download.LogPriority
import com.taisau.android.common.download.Logger


internal class DownloadLogger(val enableLog: Boolean) : Logger {
    override fun log(priority: LogPriority, tag: String, message: String , throwable: Throwable?) {
        if (!enableLog) return
        val logPriority = when (priority) {
            LogPriority.VERBOSE -> Log.VERBOSE
            LogPriority.DEBUG -> Log.DEBUG
            LogPriority.INFO -> Log.INFO
            LogPriority.WARN -> Log.WARN
            LogPriority.ERROR -> Log.ERROR
        }
        if (throwable != null) Log.println(logPriority, tag, "$message\n${Log.getStackTraceString(throwable)}")
        else Log.println(logPriority, tag, message)
    }
}
