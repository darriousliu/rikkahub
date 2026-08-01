package me.rerere.rikkahub.ui.components.webview

import java.io.File
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal object WebViewContentCache {
    private const val DIRECTORY_NAME = "webview_content"
    private const val HASH_LENGTH = 64
    private val maxAgeMillis = 7.days.inWholeMilliseconds
    private val hexDigits = "0123456789abcdef".toCharArray()

    fun store(
        cacheDir: File,
        content: String,
        nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ): String {
        val id = content.sha256()
        val directory = File(cacheDir, DIRECTORY_NAME)
        check(directory.isDirectory || directory.mkdirs()) {
            "Unable to create WebView content cache directory"
        }

        val file = File(directory, id)
        if (!file.isFile) {
            file.writeText(content)
        }
        file.setLastModified(nowMillis())

        removeExpiredFiles(directory, nowMillis)
        return id
    }

    fun load(
        cacheDir: File,
        id: String,
        nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ): String? {
        if (!id.isSha256()) return null

        val file = File(File(cacheDir, DIRECTORY_NAME), id)
        if (!file.isFile) return null

        return runCatching {
            file.readText().also {
                file.setLastModified(nowMillis())
            }
        }.getOrNull()
    }

    private fun removeExpiredFiles(directory: File, nowMillis: () -> Long) {
        val expirationTime = nowMillis() - maxAgeMillis
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < expirationTime) {
                file.delete()
            }
        }
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return CharArray(bytes.size * 2).also { result ->
            bytes.forEachIndexed { index, byte ->
                val value = byte.toInt() and 0xff
                result[index * 2] = hexDigits[value ushr 4]
                result[index * 2 + 1] = hexDigits[value and 0x0f]
            }
        }.concatToString()
    }

    private fun String.isSha256(): Boolean =
        length == HASH_LENGTH && all { it in '0'..'9' || it in 'a'..'f' }
}
