package me.rerere.document

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.io.Source
import me.rerere.common.archive.ZipFileReader
import nl.adaptivity.xmlutil.EventType

private data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String
)

object EpubParser {
    fun parse(file: PlatformFile): String {
        return try {
            ZipFileReader(file.toKotlinxIoPath()).use { zip ->
                val opfPath = findOpfPath(zip)
                    ?: return "Unable to find OPF file in EPUB"
                val opfDir = opfPath.substringBeforeLast('/', "")

                val opfEntry = zip.openEntry(opfPath)
                    ?: return "Unable to read OPF file in EPUB"
                val (manifest, spine) = opfEntry.use { parseOpf(it) }

                val result = StringBuilder()
                for (itemId in spine) {
                    val item = manifest[itemId] ?: continue
                    if (!item.mediaType.contains("html")) continue

                    val itemPath = if (opfDir.isEmpty()) item.href else "$opfDir/${item.href}"
                    val entry = zip.openEntry(itemPath) ?: continue
                    val content = entry.use { parseXhtml(it) }
                    if (content.isNotBlank()) {
                        result.append(content)
                        result.append("\n\n")
                    }
                }

                result.toString().trim().ifEmpty { "No readable content found in EPUB file" }
            }
        } catch (e: Exception) {
            "Error parsing EPUB file: ${e.message}"
        }
    }

    private fun findOpfPath(zip: ZipFileReader): String? {
        val containerEntry = zip.openEntry("META-INF/container.xml") ?: return null
        return containerEntry.use { stream ->
            val parser = DocumentXmlReader(stream)

            while (parser.eventType != EventType.END_DOCUMENT) {
                if (parser.eventType == EventType.START_ELEMENT && parser.name == "rootfile") {
                    return@use parser.getAttributeValue(null, "full-path")
                }
                parser.next()
            }
            null
        }
    }

    private fun parseOpf(inputStream: Source): Pair<Map<String, ManifestItem>, List<String>> {
        val parser = DocumentXmlReader(inputStream)

        val manifest = mutableMapOf<String, ManifestItem>()
        val spine = mutableListOf<String>()

        while (parser.eventType != EventType.END_DOCUMENT) {
            if (parser.eventType == EventType.START_ELEMENT) {
                when (parser.name) {
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                        if (id.isNotEmpty()) {
                            manifest[id] = ManifestItem(id, href, mediaType)
                        }
                    }

                    "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref") ?: ""
                        if (idref.isNotEmpty()) {
                            spine.add(idref)
                        }
                    }
                }
            }
            parser.next()
        }

        return manifest to spine
    }

    private fun parseXhtml(inputStream: Source): String {
        return try {
            val parser = DocumentXmlReader(inputStream, namespaceAware = false)

            val result = StringBuilder()
            val tagStack = ArrayDeque<String>()
            var inBody = false
            var listCounter = 0

            while (parser.eventType != EventType.END_DOCUMENT) {
                when (parser.eventType) {
                    EventType.START_ELEMENT -> {
                        val tag = parser.name.lowercase()
                        tagStack.addLast(tag)

                        when (tag) {
                            "body" -> inBody = true
                            "ol" -> listCounter = 0
                            "li" -> {
                                val parentTag = tagStack.dropLast(1).lastOrNull()
                                if (parentTag == "ol") {
                                    listCounter++
                                    result.append("$listCounter. ")
                                } else {
                                    result.append("- ")
                                }
                            }

                            "br" -> result.append("\n")
                            "img" -> {
                                if (inBody) {
                                    val alt = parser.getAttributeValue(null, "alt")
                                    if (!alt.isNullOrBlank()) {
                                        result.append("[image: $alt]")
                                    }
                                }
                            }

                            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                                if (inBody) {
                                    val level = tag[1].digitToInt()
                                    result.append("${"#".repeat(level)} ")
                                }
                            }

                            "strong", "b" -> {
                                if (inBody) result.append("**")
                            }

                            "em", "i" -> {
                                if (inBody) result.append("*")
                            }

                            "hr" -> {
                                if (inBody) result.append("\n---\n")
                            }

                            "blockquote" -> {
                                if (inBody) result.append("> ")
                            }
                        }
                    }

                    EventType.TEXT -> {
                        if (inBody) {
                            val text = parser.text
                                ?.replace('\n', ' ')
                                ?.replace('\r', ' ')
                                ?.replace("\\s+".toRegex(), " ")
                            if (!text.isNullOrBlank()) {
                                result.append(text)
                            }
                        }
                    }

                    EventType.END_ELEMENT -> {
                        val tag = parser.name.lowercase()
                        if (tagStack.isNotEmpty()) tagStack.removeLast()

                        when (tag) {
                            "body" -> inBody = false
                            "p", "div" -> {
                                if (inBody) result.append("\n\n")
                            }

                            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                                if (inBody) result.append("\n\n")
                            }

                            "li" -> {
                                if (inBody) result.append("\n")
                            }

                            "ul", "ol" -> {
                                if (inBody) result.append("\n")
                            }

                            "br" -> {}
                            "strong", "b" -> {
                                if (inBody) result.append("**")
                            }

                            "em", "i" -> {
                                if (inBody) result.append("*")
                            }

                            "blockquote" -> {
                                if (inBody) result.append("\n")
                            }
                        }
                    }
                    else -> Unit
                }
                try {
                    parser.next()
                } catch (_: Exception) {
                    break
                }
            }

            result.toString()
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        } catch (e: Exception) {
            ""
        }
    }
}
