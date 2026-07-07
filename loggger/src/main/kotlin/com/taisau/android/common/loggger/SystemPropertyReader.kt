package com.taisau.android.common.loggger

import android.util.Log

/**
 * 系统属性读取器 —— 通过反射调用隐藏 API `android.os.SystemProperties.get()`。
 *
 * 用于实现 ADB 动态控制日志开关等调试功能：
 * ```
 * adb shell setprop loggger.logcat false   # 关闭 LogcatPlant 输出
 * adb shell setprop loggger.logcat true    # 开启
 * ```
 *
 * ### 工作原理
 * Android 的 `SystemProperties` 是隐藏类（`@hide`），
 * 通过反射调用 `SystemProperties.get(String, String)` 方法读取系统属性。
 *
 * 此方式无需任何权限，在所有 Android 版本上均可工作。
 *
 * ### 性能
 * 每次调用 [get] 都涉及反射，建议配合缓存使用（如 [LogcatPlant.debugPropertyRefreshMs]）。
 */
internal object SystemPropertyReader {

    private const val TAG = "SystemPropertyReader"

    /** 反射缓存的 `SystemProperties.get(String, String)` 方法。 */
    private val getMethod: java.lang.reflect.Method? by lazy {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            clazz.getMethod("get", String::class.java, String::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot access android.os.SystemProperties", e)
            null
        }
    }

    /**
     * 读取系统属性值。
     *
     * @param key 属性名，例如 `"loggger.logcat"`、`"debug.loggger"` 等
     * @param defaultValue 属性不存在时的默认值（默认 `""`）
     * @return 属性值，失败时返回 [defaultValue]
     */
    fun get(key: String, defaultValue: String = ""): String {
        return try {
            getMethod?.invoke(null, key, defaultValue) as? String ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }
}
