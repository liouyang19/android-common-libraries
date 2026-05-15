package com.taisau.android.common.upgrade

import org.json.JSONObject

data class UpgradeInfo(
    val hasNewVersion: Boolean,
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val changeLog: String = "",
    val forceUpdate: Boolean = false,
    val fileMd5: String? = null,
    val fileSize: Long = 0,
) {
    companion object {
        @JvmStatic
        fun fromJson(json: JSONObject) = UpgradeInfo(
            hasNewVersion = json.optBoolean("hasNewVersion"),
            versionCode = json.optLong("versionCode"),
            versionName = json.optString("versionName"),
            downloadUrl = json.optString("downloadUrl"),
            changeLog = json.optString("changeLog"),
            forceUpdate = json.optBoolean("forceUpdate"),
            fileMd5 = json.optString("fileMd5").ifEmpty { null },
            fileSize = json.optLong("fileSize"),
        )
    }
}
