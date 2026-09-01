package me.rerere.rikkahub.ui.components.webview

import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/** 在内存里暂存要在 WebView 中展示的 HTML，避免把整段内容塞进导航参数。 */
internal object WebViewContentStore {
    private val content = Cache.Builder<String, String>()
        .expireAfterWrite(7.days)
        .maximumCacheSize(64)
        .build()

    fun store(html: String): String {
        val id = Uuid.random().toString()
        content.put(id, html)
        return id
    }

    fun load(id: String): String? = content.get(id)
}
