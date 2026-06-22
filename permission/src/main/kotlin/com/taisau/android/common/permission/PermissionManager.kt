package com.taisau.android.common.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

private enum class SpecialType {
    MANAGE_EXTERNAL_STORAGE,
    INSTALL_PACKAGES,
    WRITE_SETTINGS,
    SYSTEM_ALERT_WINDOW,
    PACKAGE_USAGE_STATS,
    SCHEDULE_EXACT_ALARM,
    PICTURE_IN_PICTURE,
    ACCESSIBILITY,
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberPermission(permission: Permission): PermissionHandle {
    val context = LocalContext.current

    val specialType = when (permission) {
        is Permission.ManageExternalStorage -> SpecialType.MANAGE_EXTERNAL_STORAGE
        is Permission.InstallPackages -> SpecialType.INSTALL_PACKAGES
        is Permission.WriteSettings -> SpecialType.WRITE_SETTINGS
        is Permission.SystemAlertWindow -> SpecialType.SYSTEM_ALERT_WINDOW
        is Permission.PackageUsageStats -> SpecialType.PACKAGE_USAGE_STATS
        is Permission.ScheduleExactAlarm -> SpecialType.SCHEDULE_EXACT_ALARM
        is Permission.PictureInPicture -> SpecialType.PICTURE_IN_PICTURE
        is Permission.Accessibility -> SpecialType.ACCESSIBILITY
        else -> null
    }
    if (specialType != null) {
        return remember(context, specialType) {
            SpecialPermissionHandle(context, specialType)
        }
    }

    if (permission.rawPermissions.isEmpty()) {
        return remember { EmptyPermissionHandle }
    }
    val state = rememberMultiplePermissionsState(permission.rawPermissions)
    return remember(state) { MultiplePermissionHandle(state) }
}

interface PermissionHandle {
    val isGranted: Boolean
    val shouldShowRationale: Boolean
    fun launchRequest()
}

private object EmptyPermissionHandle : PermissionHandle {
    override val isGranted = true
    override val shouldShowRationale = false
    override fun launchRequest() = Unit
}

@OptIn(ExperimentalPermissionsApi::class)
private class MultiplePermissionHandle(
    private val state: MultiplePermissionsState,
) : PermissionHandle {
    override val isGranted: Boolean get() = state.allPermissionsGranted
    override val shouldShowRationale: Boolean
        get() = state.permissions.any { it.status.shouldShowRationale }
    override fun launchRequest() = state.launchMultiplePermissionRequest()
}

/**
 * 特殊权限处理器（如 MANAGE_EXTERNAL_STORAGE、INSTALL_PACKAGES 等）。
 * 通过系统设置页授权，而非标准权限弹窗。
 */
private class SpecialPermissionHandle(
    private val context: Context,
    private val type: SpecialType,
) : PermissionHandle {
    override val isGranted: Boolean
        get() = when (type) {
            SpecialType.MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else true
            }
            SpecialType.INSTALL_PACKAGES -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.packageManager.canRequestPackageInstalls()
                } else true
            }
            SpecialType.WRITE_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.System.canWrite(context)
                } else true
            }
            SpecialType.SYSTEM_ALERT_WINDOW -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
            }
            SpecialType.PACKAGE_USAGE_STATS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName,
                    ) == AppOpsManager.MODE_ALLOWED
                } else true
            }
            SpecialType.SCHEDULE_EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(android.app.AlarmManager::class.java)
                        ?.canScheduleExactAlarms() ?: true
                } else true
            }
            SpecialType.PICTURE_IN_PICTURE -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    @Suppress("DEPRECATION")
                    val mgr = context.getSystemService("picture_in_picture")
                    if (mgr != null) {
                        try {
                            mgr::class.java.getMethod("isPictureInPicturePossible")
                                .invoke(mgr) as? Boolean ?: true
                        } catch (_: Exception) {
                            true
                        }
                    } else true
                } else {
                    Build.VERSION.SDK_INT >= 26
                }
            }
            SpecialType.ACCESSIBILITY -> {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                val enabledServices = am.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
                )
                enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
            }
        }

    override val shouldShowRationale: Boolean = false

    override fun launchRequest() {
        val intent = when (type) {
            SpecialType.MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                } else null
            }
            SpecialType.INSTALL_PACKAGES -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                } else null
            }
            SpecialType.WRITE_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                } else null
            }
            SpecialType.SYSTEM_ALERT_WINDOW -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                } else null
            }
            SpecialType.PACKAGE_USAGE_STATS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                } else null
            }
            SpecialType.SCHEDULE_EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                } else null
            }
            SpecialType.PICTURE_IN_PICTURE -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    Intent("android.settings.ACTION_MANAGE_PICTURE_IN_PICTURE_PERMISSION")
                } else null
            }
            SpecialType.ACCESSIBILITY -> {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
        }
        if (intent != null) {
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
