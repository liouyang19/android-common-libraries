package com.taisau.android.common.download.utils

/**
 * 请求头序列化工具 —— 用于 [DownloadEntity.headers] 的存贮。
 *
 * 格式：`key1:value1;key2:value2`
 * 特殊字符 `:` 和 `;` 会被编码，避免解析歧义。
 */
internal object HeadersSerializer {

    fun serialize(map: Map<String, String>): String {
        return map.entries.joinToString(";") { (key, value) ->
            "${encode(key)}:${encode(value)}"
        }
    }

    fun parse(data: String): Map<String, String> {
        if (data.isBlank()) return emptyMap()
        return data.split(";").associate { pair ->
            val idx = pair.indexOf(':')
            if (idx > 0) {
                decode(pair.substring(0, idx)) to decode(pair.substring(idx + 1))
            } else {
                pair to ""
            }
        }
    }

    private fun encode(s: String): String {
        return s.replace(":", "%3A").replace(";", "%3B").replace("=", "%3D")
    }

    private fun decode(s: String): String {
        return s.replace("%3A", ":").replace("%3B", ";").replace("%3D", "=")
    }
}
