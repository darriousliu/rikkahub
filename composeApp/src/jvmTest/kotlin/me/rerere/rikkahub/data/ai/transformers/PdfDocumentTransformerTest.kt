package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.di.commonModule
import me.rerere.rikkahub.di.jvmModule
import me.rerere.rikkahub.service.toFileUri
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PdfDocumentTransformerTest {
    @Test
    @OptIn(KoinInternalApi::class)
    fun platformBindingsDoNotOverrideCommonBindings() {
        val duplicates = commonModule.mappings.keys.intersect(jvmModule.mappings.keys)
        assertTrue(duplicates.isEmpty(), "Duplicate registrations: $duplicates")
    }

    @Test
    fun productionRegistrationParsesPagesInOrderAndKeepsEmptyPages() = withExtractor { extractor, root ->
        assertSame(JvmDocumentTextExtractor, extractor)
        val file = root.resolve("附件 + 中文.pdf").apply { writeBytes(pdfTestBytes) }
        assertEquals(
            "---Page 1:\nCMP80 First\n\n---Page 2:\n\n---Page 3:\nCMP80 Last\n\n",
            extractor.extract(PlatformFile(file), "application/pdf"),
        )
    }

    @Test
    fun transformerAddsPdfTextAndKeepsAttachmentAndQuestion() = withExtractor { _, root ->
        val file = root.resolve("附件 + 中文.pdf").apply { writeBytes(pdfTestBytes) }
        val input = message(file)
        val output = DocumentAsPromptTransformer.transform(context(), listOf(input)).single()
        assertEquals(input.parts, output.parts.drop(1))
        val prompt = (output.parts.first() as UIMessagePart.Text).text
        assertTrue(prompt.contains("<UploadFile name=\"附件 + 中文.pdf\">"))
        assertTrue(prompt.contains("CMP80 First\n\n---Page 2:\n\n---Page 3:\nCMP80 Last"))
    }

    @Test
    fun malformedPdfUsesExistingReadError() = withExtractor { _, root ->
        val file = root.resolve("broken.pdf").apply { writeText("not a PDF") }
        val output = DocumentAsPromptTransformer.transform(context(), listOf(message(file))).single()
        val prompt = (output.parts.first() as UIMessagePart.Text).text
        assertTrue(prompt.contains("[ERROR, failed to read file: broken.pdf]"))
    }

    @Test
    fun pdfRegistrationStillDelegatesOfficeDocuments() = withExtractor { extractor, root ->
        val file = root.resolve("test.docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("<document><body><p><r><t>DOCX 中文</t></r></p></body></document>".toByteArray())
            zip.closeEntry()
        }
        assertEquals(
            OfficeDocumentTextExtractor.extract(PlatformFile(file), DOCX_MIME),
            extractor.extract(PlatformFile(file), DOCX_MIME),
        )
    }

    private fun withExtractor(block: suspend (DocumentTextExtractor, java.io.File) -> Unit) = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val app = koinApplication(createEagerInstances = false) { modules(commonModule, jvmModule, module { single<CoroutineScope> { scope } }) }
        val root = Files.createTempDirectory("pdf-transformer-").toFile()
        val extractor = app.koin.get<DocumentTextExtractor>()
        startKoin { modules(module { single<DocumentTextExtractor> { extractor } }) }
        try {
            block(extractor, root)
        } finally {
            stopKoin()
            app.close()
            scope.cancel()
            root.deleteRecursively()
        }
    }

    private fun message(file: java.io.File) = UIMessage.user("question").copy(parts = listOf(
        UIMessagePart.Text("question"),
        UIMessagePart.Document(PlatformFile(file).toFileUri(), file.name, "application/pdf"),
    ))

    private fun context() = TransformerContext(Model(), Assistant(), Settings())

    private companion object {
        const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
