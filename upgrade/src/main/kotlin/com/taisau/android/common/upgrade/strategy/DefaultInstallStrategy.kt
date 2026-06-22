package com.taisau.android.common.upgrade.strategy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * [InstallStrategy] 的默认实现。
 *
 * 使用 [FileProvider] + [Intent.ACTION_VIEW] 触发 APK 安装。
 *
 * @param context Application Context
 * @param authority FileProvider authority（默认 `${packageName}.fileprovider`）
 */
class DefaultInstallStrategy(
    private val context: Context,
    private val authority: String = "${context.packageName}.fileprovider",
) : InstallStrategy {

    override fun install(savePath: String) {
        val file = File(savePath)
        if (!file.exists()) return

        val installUri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(context, authority, file)
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(installUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
