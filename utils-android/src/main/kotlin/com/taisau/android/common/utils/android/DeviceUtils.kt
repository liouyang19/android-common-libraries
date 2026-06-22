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
import android.text.TextUtils
import java.io.File
import java.net.NetworkInterface
import java.util.Locale
import java.util.UUID

/**
 * 设备信息工具类
 * 
 * 提供获取设备硬件信息、系统信息、网络信息、存储信息、电池状态等功能。
 * 所有方法均为静态方法，通过对象名直接调用。
 */
object DeviceUtils {

    /**
     * 设备品牌名称
     * 例如：Samsung, Xiaomi, Huawei
     */
    val brand: String get() = Build.BRAND

    /**
     * 设备型号
     * 例如：Galaxy S21, Mi 11
     */
    val model: String get() = Build.MODEL

    /**
     * 设备制造商
     * 例如：samsung, xiaomi, huawei
     */
    val manufacturer: String get() = Build.MANUFACTURER

    /**
     * Android SDK 版本号
     * 例如：30 (Android 11), 33 (Android 13)
     */
    val sdkInt: Int get() = Build.VERSION.SDK_INT

    /**
     * Android 版本代号
     * 例如：REL (正式版本)
     */
    val sdkName: String get() = Build.VERSION.CODENAME

    /**
     * Android 系统版本号
     * 例如：11, 12, 13
     */
    val release: String get() = Build.VERSION.RELEASE

    /**
     * 设备主板名称
     */
    val board: String get() = Build.BOARD

    /**
     * 设备设计名称
     */
    val device: String get() = Build.DEVICE

    /**
     * 设备硬件名称
     */
    val hardware: String get() = Build.HARDWARE

    /**
     * 产品名称
     */
    val product: String get() = Build.PRODUCT

    /**
     * 系统构建显示 ID
     * 例如：RP1A.200720.012
     */
    val display: String get() = Build.DISPLAY

    /**
     * 系统构建指纹标识
     * 唯一标识当前系统构建
     */
    val fingerprint: String get() = Build.FINGERPRINT

    /**
     * 设备序列号
     * 
     * Android 10 (Q) 及以上版本需要 READ_PRIVILEGED_PHONE_STATE 权限，
     * 无权限时返回 "unknown"
     */
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

    /**
     * 构建类型
     * 例如：user (用户版本), eng (工程版本), userdebug (调试版本)
     */
    val type: String get() = Build.TYPE

    /**
     * 构建标签
     * 例如：release-keys, test-keys
     */
    val tags: String get() = Build.TAGS

    /**
     * 构建主机名
     */
    val host: String get() = Build.HOST

    /**
     * 构建用户名
     */
    val user: String get() = Build.USER

    /**
     * 引导加载程序版本号
     */
    val bootloader: String get() = Build.BOOTLOADER

    /**
     * 无线电固件版本号
     */
    val radioVersion: String get() = Build.getRadioVersion() ?: "unknown"

    /**
     * 系统版本的增量值
     */
    val incremental: String get() = Build.VERSION.INCREMENTAL

    /**
     * 获取当前系统语言代码
     * 
     * @return 语言代码，例如：zh, en, ja
     */
    fun getLanguage(): String {
        return Locale.getDefault().language
    }

    /**
     * 获取当前国家/地区代码
     * 
     * @return 国家代码，例如：CN, US, JP
     */
    fun getCountry(): String {
        return Locale.getDefault().country
    }

    /**
     * 获取当前语言的显示名称
     * 
     * @return 语言显示名称，例如：中文, English, 日本語
     */
    fun getDisplayLanguage(): String {
        return Locale.getDefault().displayLanguage
    }

    /**
     * 获取当前语言标签（BCP 47 格式）
     * 
     * @return 语言标签，例如：zh-CN, en-US, ja-JP
     */
    fun getLanguageTag(): String {
        return Locale.getDefault().toLanguageTag()
    }

    /**
     * 获取 WLAN 接口的 MAC 地址
     * 
     * Android 6.0+ 出于隐私保护，此方法可能返回固定值或 null。
     * 建议使用 Android ID 或其他标识符替代。
     * 
     * @return MAC 地址字符串（格式：xx:xx:xx:xx:xx:xx），获取失败返回 null
     */
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

    /**
     * 获取 Android ID
     * 
     * Android ID 是在设备首次启动时生成的 64 位随机数，
     * 恢复出厂设置后会改变。同一厂商的不同设备可能有相同的 Android ID。
     * 
     * @param context 上下文对象
     * @return Android ID 字符串，获取失败返回空字符串
     */
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    /**
     * 检测设备是否已 Root
     * 
     * 通过检查常见的 Root 文件路径和 su 命令来判断。
     * 此检测方法可能被某些 Root 管理工具绕过，不适用于安全敏感场景。
     * 
     * @return true 表示设备已 Root，false 表示未 Root
     */
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

    /**
     * 检测当前是否运行在模拟器中
     * 
     * 通过检查多个 Build 字段特征来判断，包括：
     * - FINGERPRINT 包含 "generic" 或 "unknown"
     * - MODEL 包含模拟器相关关键词
     * - HARDWARE 为 goldfish/ranchu/vbox86
     * - PRODUCT 为 sdk/google_sdk 等
     * 
     * @return true 表示是模拟器，false 表示是真机
     */
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

    /**
     * 检测设备是否为平板
     * 
     * 通过屏幕尺寸配置判断，large 或 xlarge 屏幕被视为平板。
     * 
     * @param context 上下文对象
     * @return true 表示是平板，false 表示是手机或其他设备
     */
    fun isTablet(context: Context): Boolean {
        return context.resources.configuration.screenLayout and
                android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK >=
                android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    /**
     * 获取设备总内存大小
     * 
     * 通过读取 /proc/meminfo 文件获取 MemTotal 值。
     * 
     * @return 总内存大小（字节），获取失败返回 -1
     */
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

    /**
     * 获取可用内存大小
     * 
     * 通过 ActivityManager 获取当前系统可用内存。
     * 
     * @param context 上下文对象
     * @return 可用内存大小（字节）
     */
    fun getAvailableRAM(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.availMem
    }

    /**
     * 获取内部存储总容量
     * 
     * 通过 StatFs 统计 /data 分区的总块数和块大小计算得出。
     * 
     * @return 总容量（字节）
     */
    fun getTotalInternalStorage(): Long {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        return stat.blockCountLong * stat.blockSizeLong
    }

    /**
     * 获取内部存储可用容量
     * 
     * 通过 StatFs 统计 /data 分区的可用块数和块大小计算得出。
     * 
     * @return 可用容量（字节）
     */
    fun getAvailableInternalStorage(): Long {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /**
     * 获取屏幕亮度值
     * 
     * @param context 上下文对象
     * @return 亮度值（0-255），获取失败返回 -1
     */
    fun getScreenBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            -1
        }
    }

    /**
     * 设置屏幕亮度
     * 
     * 需要 WRITE_SETTINGS 权限（Android 6.0+）。
     * 亮度值会自动限制在 0-255 范围内。
     * 
     * @param context 上下文对象
     * @param brightness 亮度值（0-255），0 最暗，255 最亮
     */
    fun setScreenBrightness(context: Context, brightness: Int) {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightness.coerceIn(0, 255)
        )
    }

    /**
     * 检测屏幕是否亮起
     * 
     * Android 4.4+ 使用 isInteractive，之前版本使用 isScreenOn。
     * 
     * @param context 上下文对象
     * @return true 表示屏幕亮起，false 表示屏幕关闭
     */
    fun isScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm.isInteractive
        } else {
            pm.isScreenOn
        }
    }

    /**
     * 获取电池电量百分比
     * 
     * 通过广播接收器获取当前电池状态信息。
     * 
     * @param context 上下文对象
     * @return 电池电量百分比（0-100），获取失败返回 -1
     */
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

    /**
     * 检测设备是否正在充电
     * 
     * 通过广播接收器获取电池状态，判断是否为充电中或已充满。
     * 
     * @param context 上下文对象
     * @return true 表示正在充电或已充满，false 表示未充电
     */
    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    /**
     * 获取设备唯一标识符
     * 
     * Android 10 (Q) 及以上版本由于隐私限制，统一返回 Android ID。
     * Android 10 以下版本优先尝试获取 IMEI/MEID，失败则返回 Android ID。
     * 
     * 注意：
     * - Android 10+ 无法获取 IMEI，需要 READ_PRIVILEGED_PHONE_STATE 权限
     * - Android ID 在恢复出厂设置后会改变
     * - 不建议将此作为用户追踪的唯一标识
     * 
     * @param context 上下文对象
     * @return 设备标识符字符串，获取失败返回 null
     */
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

    /**
     * 获取 Android ID
     *
     * @param context 上下文对象
     * @return Android ID 字符串
     */
    fun getUniqueDeviceId(context: Context): String {
        try {
            val androidId = getAndroidId(context)
            if (!TextUtils.isEmpty(androidId)){
                return  getUUIDid(""+2,androidId)
            }
        }catch (_: Exception){ }
        return  getUUIDid(""+9,"")
    }


    private fun getUUIDid(prefix: String,id: String): String{
        if (id == ""){
            return prefix + UUID.randomUUID().toString().replace("-", "")
        }
        return prefix + UUID.nameUUIDFromBytes(id.toByteArray()).toString().replace("-", "")

    }
}
