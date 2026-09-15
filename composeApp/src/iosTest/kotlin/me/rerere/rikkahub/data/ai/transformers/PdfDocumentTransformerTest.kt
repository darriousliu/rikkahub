package me.rerere.rikkahub.data.ai.transformers

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.di.commonModule
import me.rerere.rikkahub.di.iosModule
import me.rerere.rikkahub.service.toFileUri
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PdfDocumentTransformerTest {
    @Test
    @OptIn(KoinInternalApi::class)
    fun platformBindingsDoNotOverrideCommonBindings() {
        val duplicates = commonModule.mappings.keys.intersect(iosModule(PdfTextExtractor { "" }).mappings.keys)
        assertTrue(duplicates.isEmpty(), "Duplicate registrations: $duplicates")
    }

    @Test
    fun productionRegistrationPassesFileUrlToInjectedPdfReader() = runTest {
        var receivedPath: String? = null
        val pdf = PdfTextExtractor { url ->
            receivedPath = url.path
            "---Page 1:\nInjected PDF text\n"
        }
        withExtractor(pdf) { extractor, file ->
            val input = message(file)
            val output = DocumentAsPromptTransformer.transform(context(), listOf(input)).single()
            assertEquals(file.toString(), receivedPath)
            assertEquals(input.parts, output.parts.drop(1))
            assertTrue((output.parts.first() as UIMessagePart.Text).text.contains("Injected PDF text"))
            assertNull(extractor.extract(PlatformFile(file.toString()), "text/plain"))
        }
    }

    @Test
    fun injectedPdfFailureUsesExistingReadError() = runTest {
        withExtractor(PdfTextExtractor { error("PDF failed") }) { _, file ->
            val output = DocumentAsPromptTransformer.transform(context(), listOf(message(file))).single()
            val prompt = (output.parts.first() as UIMessagePart.Text).text
            assertTrue(prompt.contains("[ERROR, failed to read file: ${file.name}]"))
        }
    }

    private suspend fun withExtractor(
        pdf: PdfTextExtractor,
        block: suspend (DocumentTextExtractor, Path) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val app = koinApplication(createEagerInstances = false) { modules(commonModule, iosModule(pdf), module { single<CoroutineScope> { scope } }) }
        assertSame(pdf, app.koin.get<PdfTextExtractor>())
        val extractor = app.koin.get<DocumentTextExtractor>()
        startKoin { modules(module { single<DocumentTextExtractor> { extractor } }) }
        val file = Path(SystemTemporaryDirectory, "${Uuid.random()} + 中文.pdf")
        SystemFileSystem.sink(file).buffered().use { it.write(pdfTestBytes) }
        try {
            block(extractor, file)
        } finally {
            stopKoin()
            app.close()
            scope.cancel()
            SystemFileSystem.delete(file)
        }
    }

    private fun message(file: Path) = UIMessage.user("question").copy(parts = listOf(
        UIMessagePart.Text("question"),
        UIMessagePart.Document(PlatformFile(file.toString()).toFileUri(), file.name, "application/pdf"),
    ))

    private fun context() = TransformerContext(Model(), Assistant(), Settings())
}
