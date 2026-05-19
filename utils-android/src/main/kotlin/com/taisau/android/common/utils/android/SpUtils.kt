package com.taisau.android.common.utils.android

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

open class SpUtils(context: Context, name: String) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun putString(key: String, value: String) {
        sp.edit { putString(key, value) }
    }

    fun getString(key: String, default: String = ""): String {
        return sp.getString(key, default) ?: default
    }

    fun putInt(key: String, value: Int) {
        sp.edit { putInt(key, value) }
    }

    fun getInt(key: String, default: Int = 0): Int {
        return sp.getInt(key, default)
    }

    fun putBoolean(key: String, value: Boolean) {
        sp.edit { putBoolean(key, value) }
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return sp.getBoolean(key, default)
    }

    fun putLong(key: String, value: Long) {
        sp.edit { putLong(key, value) }
    }

    fun getLong(key: String, default: Long = 0L): Long {
        return sp.getLong(key, default)
    }

    fun putFloat(key: String, value: Float) {
        sp.edit { putFloat(key, value) }
    }

    fun getFloat(key: String, default: Float = 0f): Float {
        return sp.getFloat(key, default)
    }

    fun putStringSet(key: String, values: Set<String>) {
        sp.edit { putStringSet(key, values) }
    }

    fun getStringSet(key: String, default: Set<String> = emptySet()): Set<String> {
        return sp.getStringSet(key, default) ?: default
    }

    fun remove(key: String) {
        sp.edit { remove(key) }
    }

    fun clear() {
        sp.edit { clear() }
    }

    fun contains(key: String): Boolean {
        return sp.contains(key)
    }

    fun getAll(): Map<String, *> {
        return sp.all
    }
}
