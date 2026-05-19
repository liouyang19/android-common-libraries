package com.taisau.android.common.utils.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.telephony.TelephonyManager
import java.io.File
import java.net.NetworkInterface
import java.util.Locale

object DeviceUtils {

    val brand: String get() = Build.BRAND
    val model: String get() = Build.MODEL
    val manufacturer: String get() = Build.MANUFACTURER
    val sdkInt: Int get() = Build.VERSION.SDK_INT
    val sdkName: String get() = Build.VERSION.CODENAME
    val release: String get() = Build.VERSION.RELEASE
    val board: String get() = Build.BOARD
    val device: String get() = Build.DEVICE
    val hardware: String get() = Build.HARDWARE
    val product: String get() = Build.PRODUCT
    val display: String get() = Build.DISPLAY
    val fingerprint: String get() = Build.FINGERPRINT
    val serial: String get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Build.getSerial()
            } catch (e: SecurityException) {
                "unknown"
            }
        } else {
            Build.SERIAL
        }
    val type: String get() = Build.TYPE
    val tags: String get() = Build.TAGS
    val host: String get() = Build.HOST
    val user: String get() = Build.USER
    val bootloader: String get() = Build.BOOTLOADER
    val radioVersion: String get() = Build.getRadioVersion() ?: "unknown"
    val incremental: String get() = Build.VERSION.INCREMENTAL

    fun getLanguage(): String {
        return Locale.getDefault().language
    }

    fun getCountry(): String {
        return Locale.getDefault().country
    }

    fun getDisplayLanguage(): String {
        return Locale.getDefault().displayLanguage
    }

    fun getLanguageTag(): String {
        return Locale.getDefault().toLanguageTag()
    }

    fun getMacAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.name == "wlan0") {
                    val macBytes = networkInterface.hardwareAddress ?: return null
                    return macBytes.joinToString(":") { "%02x".format(it) }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return try {
            Runtime.getRuntime().exec(arrayOf("which", "su")).inputStream.bufferedReader()
                .readLine() != null
        } catch (e: Exception) {
            false
        }
    }

    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.lowercase().contains("emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.HARDWARE == "goldfish" ||
                Build.HARDWARE == "ranchu" ||
                Build.HARDWARE == "vbox86" ||
                Build.PRODUCT == "sdk" ||
                Build.PRODUCT == "google_sdk" ||
                Build.PRODUCT == "sdk_x86" ||
                Build.PRODUCT == "vbox86p" ||
                Build.BOARD.lowercase().contains("generic") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    }

    fun isTablet(context: Context): Boolean {
        return context.resources.configuration.screenLayout and
                android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK >=
                android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    fun getTotalRAM(): Long {
        return try {
            val reader = File("/proc/meminfo").bufferedReader()
            val line = reader.readLine()
            val parts = line.split("\\s+".toRegex())
            parts[1].toLong() * 1024
        } catch (e: Exception) {
            -1L
        }
    }

    fun getAvailableRAM(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.availMem
    }

    fun getTotalInternalStorage(): Long {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        return stat.blockCountLong * stat.blockSizeLong
    }

    fun getAvailableInternalStorage(): Long {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun getScreenBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            -1
        }
    }

    fun setScreenBrightness(context: Context, brightness: Int) {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightness.coerceIn(0, 255)
        )
    }

    fun isScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm.isInteractive
        } else {
            pm.isScreenOn
        }
    }

    fun getBatteryPercentage(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            -1
        }
    }

    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    fun getDeviceId(context: Context): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getAndroidId(context)
        } else {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                tm.deviceId
            } catch (e: SecurityException) {
                getAndroidId(context)
            }
        }
    }
}
