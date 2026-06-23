package com.taisau.android.common.download

import com.taisau.android.common.download.utils.DownloadLogger

interface Logger {
	
	companion object {
		const val TAG = "DownloadLogs"
	}
	
	fun log(
		priority: LogPriority = LogPriority.DEBUG,
		tag: String = TAG,
		message: String = "",
		throwable: Throwable? = null
	)
	
}




fun Logger.v(tag: String = Logger.TAG, message: String = "",throwable: Throwable? = null) {
	log(LogPriority.VERBOSE,tag, message, throwable)
}
fun Logger.d(tag: String = Logger.TAG, message: String = "",throwable: Throwable? = null) {
	log(LogPriority.DEBUG,tag, message, throwable)
}
fun Logger.i(tag: String = Logger.TAG, message: String = "",throwable: Throwable? = null) {
	log(LogPriority.INFO,tag, message, throwable)
}
fun Logger.w(tag: String = Logger.TAG, message: String = "",throwable: Throwable? = null) {
	log(LogPriority.WARN,tag, message, throwable)
}

fun Logger.e(tag: String = Logger.TAG, message: String = "",throwable: Throwable? = null) {
	log(LogPriority.ERROR,tag, message, throwable)
}
enum class LogPriority { VERBOSE, DEBUG, INFO, WARN, ERROR }