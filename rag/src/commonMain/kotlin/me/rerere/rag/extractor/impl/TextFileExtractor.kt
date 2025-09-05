package me.rerere.rag.extractor.impl

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.runBlocking
import me.rerere.rag.extractor.DataExtractor

/**
 * 从文本文件中提取文本内容
 * 支持常见的文本文件格式
 */
class TextFileExtractor : DataExtractor<PlatformFile> {
    /**
     * 从文件中提取文本内容
     * @param data 文本文件
     * @return 提取出的文本内容
     */
    override fun extract(data: PlatformFile): List<String> {
        if (!data.exists() || !data.isRegularFile()) {
            return emptyList()
        }

        return try {
            val text = runBlocking { data.readString() }
            listOf(text)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
