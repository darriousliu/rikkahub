package me.rerere.rikkahub.data.ai.transformers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.testFilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.toLocalFilePath
import me.rerere.rikkahub.utils.canonicalFile
import me.rerere.rikkahub.utils.deleteRecursively
import me.rerere.rikkahub.utils.listFiles
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.readText
import me.rerere.rikkahub.utils.resolve
import me.rerere.rikkahub.utils.writeText
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class Base64ImageTransformerContractTest {
    private val context = TransformerContext(Model(), Assistant(), Settings())
    private val root = Path(SystemTemporaryDirectory, "cmp-base64-transformer-${Uuid.random()}")
        .canonicalFile.apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val filesManager = testFilesManager(root, scope)

    init {
        startKoin { modules(module { single<FilesManager> { filesManager } }) }
    }

    @AfterTest
    fun close() {
        stopKoin()
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun convertsEachInlineImageInOrderAndPreservesOtherPartsAndMessageMetadata() = runTest {
        val metadata = JsonObject(mapOf("keep" to JsonPrimitive("metadata")))
        val first = image().copy(metadata = metadata)
        val repeated = image().copy(metadata = metadata)
        val other = listOf(
            UIMessagePart.Text("unchanged"),
            UIMessagePart.Image("https://example.invalid/image.png"),
            UIMessagePart.Image("file:///upload/existing.png"),
            UIMessagePart.Image("data:Image/png;base64,AQID"),
            UIMessagePart.Tool("id", "tool", "{}", output = listOf(first)),
        )
        val input = listOf(
            UIMessage.user("unused").copy(parts = listOf(first) + other, translation = "translation"),
            UIMessage.user("unused").copy(parts = listOf(repeated)),
        )
        val output = Base64ImageToLocalFileTransformer.onGenerationFinish(context, input)
        val firstImage = output[0].parts.first() as UIMessagePart.Image
        val secondImage = output[1].parts.first() as UIMessagePart.Image
        assertNotEquals(firstImage.url, secondImage.url)
        val records = withContext(Dispatchers.Default) {
            withTimeout(5_000) { filesManager.observe().first { it.size == 2 } }
        }
        assertEquals(setOf("image.png"), records.map { it.displayName }.toSet())
        assertEquals(setOf("image/png"), records.map { it.mimeType }.toSet())
        assertTrue(records.all { it.sizeBytes > 0 })
        assertEquals(records.map { filesManager.getFile(it) }.toSet(),
            setOf(Path(firstImage.url.toLocalFilePath()), Path(secondImage.url.toLocalFilePath())))
        assertEquals(listOf(
            input[0].copy(parts = listOf(first.copy(url = firstImage.url)) + other),
            input[1].copy(parts = listOf(repeated.copy(url = secondImage.url))),
        ), output)
        other.forEachIndexed { index, part -> assertSame(part, output[0].parts[index + 1]) }
        assertEquals(emptyList(), Base64ImageToLocalFileTransformer.onGenerationFinish(context, emptyList()))
        assertEquals(2, filesManager.countChatFiles().first)
    }

    @Test
    fun malformedBase64FailsBeforeWritingOrProcessingLaterImages() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Base64ImageToLocalFileTransformer.onGenerationFinish(context, listOf(
                UIMessage.user("unused").copy(parts = listOf(
                    UIMessagePart.Image("data:image/png;base64,!invalid!"), image(),
                )),
            ))
        }
        assertEquals(0 to 0L, filesManager.countChatFiles())
        assertTrue(filesManager.list().isEmpty())
    }

    @Test
    fun fileFailurePropagatesAndStopsTheFinishPipeline() = runTest {
        val blocker = root.resolve("upload").apply { writeText("original") }
        var reachedNextTransformer = false
        val next = object : OutputMessageTransformer {
            override suspend fun onGenerationFinish(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
                reachedNextTransformer = true
                return messages
            }
        }
        assertFailsWith<IOException> {
            listOf(UIMessage.user("unused").copy(parts = listOf(image(), image())))
                .onGenerationFinish(
                    listOf(Base64ImageToLocalFileTransformer, next), context.model, context.assistant, context.settings,
                )
        }
        assertFalse(reachedNextTransformer)
        assertEquals("original", blocker.readText())
        assertEquals(listOf(blocker), root.listFiles().orEmpty())
        assertTrue(filesManager.list().isEmpty())
    }

    @Test
    fun laterInvalidImageKeepsEarlierWriteAndDoesNotProcessRemainingImages() = runTest {
        assertFailsWith<NullPointerException> {
            Base64ImageToLocalFileTransformer.onGenerationFinish(context, listOf(
                UIMessage.user("unused").copy(parts = listOf(
                    image(), UIMessagePart.Image("data:image/png;base64,AQID"), image(),
                )),
            ))
        }
        assertEquals(1, filesManager.countChatFiles().first)
        withContext(Dispatchers.Default) { withTimeout(5_000) { filesManager.observe().first { it.size == 1 } } }
    }

    @Test
    fun cancellationPropagatesWithoutConvertingTheRemainingImages() = runTest {
        var cancelled = false
        launch {
            currentCoroutineContext().cancel()
            try {
                Base64ImageToLocalFileTransformer.onGenerationFinish(context, listOf(
                    UIMessage.user("unused").copy(parts = listOf(image(), image())),
                ))
            } catch (_: CancellationException) {
                cancelled = true
            }
        }.join()
        assertTrue(cancelled)
        assertEquals(0 to 0L, filesManager.countChatFiles())
        assertTrue(filesManager.list().isEmpty())
    }

    private fun image() = UIMessagePart.Image(
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=",
    )
}
