package me.rerere.rikkahub.ui.components.webview

import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal object JvmWebViewContentStore {
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
