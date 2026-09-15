package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.archive.PlatformZipArchive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.toFileUri
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class OfficeDocumentTransformerTest {
    @Test
    fun addsParsedTextAndKeepsOriginalAttachments() = runTest {
        startKoin { modules(module { single<DocumentTextExtractor> { OfficeDocumentTextExtractor } }) }
        val root = Path(SystemTemporaryDirectory, "office-transformer-${Uuid.random()}")
        SystemFileSystem.createDirectories(root)
        val files = mutableListOf<Path>()
        try {
            val formats = listOf(
                Triple("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", mapOf(
                    "word/document.xml" to "<document><body><p><r><t>DOCX 中文</t></r></p></body></document>",
                )),
                Triple("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", mapOf(
                    "ppt/slides/slide1.xml" to "<sld><sp><p><r><t>PPTX 中文</t></r></p></sp></sld>",
                )),
                Triple("epub", "application/epub+zip", mapOf(
                    "META-INF/container.xml" to "<container><rootfile full-path=\"content.opf\"/></container>",
                    "content.opf" to "<package><manifest><item id=\"a\" href=\"a.xhtml\" media-type=\"application/xhtml+xml\"/></manifest><spine><itemref idref=\"a\"/></spine></package>",
                    "a.xhtml" to "<html><body><p>EPUB 中文</p></body></html>",
                )),
            )
            val documents = formats.map { (extension, mime, entries) ->
                val path = Path(root, "附件 + 中文.$extension").also { files.add(it) }
                PlatformZipArchive.create(SystemFileSystem.sink(path).buffered()) {
                    entries.forEach { (name, xml) -> add(name, Buffer().apply { write(xml.encodeToByteArray()) }) }
                }
                UIMessagePart.Document(PlatformFile(path.toString()).toFileUri(), path.name, mime)
            }
            val input = UIMessage.user("original question").copy(parts = listOf(UIMessagePart.Text("original question")) + documents)
            val output = DocumentAsPromptTransformer.transform(
                TransformerContext(Model(), Assistant(), Settings()), listOf(input),
            ).single()
            assertEquals(input.parts, output.parts.takeLast(input.parts.size))
            val prompts = output.parts.take(3).map { (it as UIMessagePart.Text).text }
            assertEquals(listOf("epub", "pptx", "docx"), prompts.map { text ->
                formats.first { text.contains("附件 + 中文.${it.first}") }.first
            })
            formats.forEach { (extension, _, _) ->
                val text = prompts.single { it.contains("附件 + 中文.$extension") }
                assertTrue(text.contains("<UploadFile name=\"附件 + 中文.$extension\">"))
                assertTrue(text.contains("${extension.uppercase()} 中文"))
                assertTrue(text.contains("</UploadFile>"))
            }
        } finally {
            stopKoin()
            files.forEach { SystemFileSystem.delete(it) }
            SystemFileSystem.delete(root)
        }
    }
}
