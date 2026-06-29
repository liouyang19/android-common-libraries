package com.taisau.android.http.client.plugins

import com.taisau.android.http.client.ApiClientConfig
import com.taisau.android.http.client.ApiPlugin
import com.taisau.android.http.client.ApiPluginKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Cookie 管理插件。
 *
 * 支持内存持久化和自定义存储。通过 [okHttp] 定制器设置 [CookieJar]。
 *
 * 使用示例：
 * ```kotlin
 * // 内存持久化
 * val client = ApiClient {
 *     install(Cookies) {
 *         persistent = false
 *     }
 * }
 *
 * // 持久化到文件
 * val client = ApiClient {
 *     install(Cookies) {
 *         persistent = true
 *         storage = PersistentCookieStorage(cacheDir)
 *     }
 * }
 * ```
 */
object Cookies : ApiPlugin<Cookies.Config> {

    override val key = ApiPluginKey<Config>("Cookies")

    data class Config(
        /** 是否持久化到磁盘，false 表示仅内存保存 */
        val persistent: Boolean = false,
        /**
         * 自定义存储。
         * 不设置时根据 [persistent] 使用 [MemoryCookieStorage] 或 [PersistentCookieStorage]
         */
        val storage: CookieStorage? = null,
    )

    override fun createConfig() = Config()

    override fun ApiClientConfig.prepare(config: Config) {
        val jar = when {
            config.storage != null -> StorageCookieJar(config.storage)
            config.persistent -> {
                throw IllegalArgumentException(
                    "Persistent cookies require an explicit storage. " +
                        "Create a PersistentCookieStorage(contextDir) and pass it to storage = ..."
                )
            }
            else -> StorageCookieJar(MemoryCookieStorage())
        }

        okHttp { cookieJar(jar) }
    }
}

// ═══════════════════════════════════════════════
// Cookie 存储接口 & 实现
// ═══════════════════════════════════════════════

/**
 * Cookie 存储接口。
 * 实现此接口可自定义 Cookie 的持久化方式。
 */
interface CookieStorage {
    /** 加载所有已保存的 Cookie */
    fun loadAll(): List<Cookie>

    /** 保存 Cookie */
    fun save(url: HttpUrl, cookies: List<Cookie>)

    /** 移除指定 URL 的 Cookie */
    fun remove(url: HttpUrl)

    /** 清空所有 Cookie */
    fun clear()
}

/**
 * 内存 Cookie 存储（App 重启后丢失）。
 */
class MemoryCookieStorage : CookieStorage {
    private val cookies = mutableMapOf<String, MutableList<Cookie>>()

    override fun loadAll(): List<Cookie> = cookies.values.flatten()

    override fun save(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val key = cookie.domain + cookie.path
            this.cookies.getOrPut(key) { mutableListOf() }.apply {
                removeAll { it.name == cookie.name }
                add(cookie)
            }
        }
    }

    override fun remove(url: HttpUrl) {
        cookies.entries.removeAll { (_, list) ->
            list.removeAll { it.matches(url) }
            list.isEmpty()
        }
    }

    override fun clear() {
        cookies.clear()
    }
}

/**
 * 文件持久化 Cookie 存储。
 *
 * @param directory 存储目录
 */
class PersistentCookieStorage(
    private val directory: java.io.File,
) : CookieStorage {
    private val cache = mutableMapOf<String, MutableList<Cookie>>()

    init {
        directory.mkdirs()
        loadFromDisk()
    }

    override fun loadAll(): List<Cookie> = cache.values.flatten()

    override fun save(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val key = "${cookie.domain}:${cookie.path}:${cookie.name}"
            cache.getOrPut(key) { mutableListOf() }.apply {
                removeAll { it.name == cookie.name }
                add(cookie)
            }
        }
        flushToDisk()
    }

    override fun remove(url: HttpUrl) {
        cache.entries.removeAll { (_, list) ->
            list.removeAll { it.matches(url) }
            list.isEmpty()
        }
        flushToDisk()
    }

    override fun clear() {
        cache.clear()
        flushToDisk()
    }

    private fun loadFromDisk() {
        directory.listFiles()?.forEach { file ->
            try {
                val lines = file.readLines()
                if (lines.size >= 5) {
                    val cookie = Cookie.Builder()
                        .domain(lines[0])
                        .path(lines[1])
                        .name(lines[2])
                        .value(lines[3])
                        .expiresAt(lines[4].toLongOrNull() ?: Long.MAX_VALUE)
                        .apply {
                            if (lines.getOrNull(5) == "true") httpOnly()
                            if (lines.getOrNull(6) == "true") secure()
                        }
                        .build()
                    val key = "${lines[0]}:${lines[1]}:${lines[2]}"
                    cache.getOrPut(key) { mutableListOf() }.add(cookie)
                }
            } catch (_: Exception) {
                file.delete()
            }
        }
    }

    private fun flushToDisk() {
        directory.listFiles()?.forEach { it.delete() }
        cache.values.flatten().forEach { cookie ->
            val file = java.io.File(
                directory,
                "${cookie.domain}_${cookie.name.replace(" ", "_")}.cookie"
            )
            try {
                file.writeText(
                    buildString {
                        appendLine(cookie.domain)
                        appendLine(cookie.path)
                        appendLine(cookie.name)
                        appendLine(cookie.value)
                        appendLine(cookie.expiresAt.toString())
                        appendLine(cookie.httpOnly.toString())
                        appendLine(cookie.secure.toString())
                    }
                )
            } catch (_: Exception) { /* skip */ }
        }
    }
}

/** 将 [CookieStorage] 适配为 OkHttp 的 [CookieJar] */
internal class StorageCookieJar(
    private val storage: CookieStorage,
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        storage.save(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return storage.loadAll().filter { cookie ->
            cookie.matches(url)
        }
    }
}
