package me.rerere.rikkahub.data.ai.transformers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class Base64ImageTransformerContractTest {
    private val context = TransformerContext(Model(), Assistant(), Settings())

    @AfterTest
    fun close() = stopKoin()

    @Test
    fun convertsEachInlineImageInOrderAndPreservesOtherPartsAndMessageMetadata() = runTest {
        val received = mutableListOf<List<Byte>>()
        bind(Base64ImageStore { bytes ->
            received += bytes.toList()
            "file:///upload/${received.size}.png"
        })
        val metadata = JsonObject(mapOf("keep" to JsonPrimitive("metadata")))
        val first = UIMessagePart.Image("data:image/jpeg;base64,AQID", metadata)
        val repeated = UIMessagePart.Image("data:image/png;base64,AQID", metadata)
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
        assertEquals(listOf(listOf<Byte>(1, 2, 3), listOf<Byte>(1, 2, 3)), received)
        assertEquals(listOf(
            input[0].copy(parts = listOf(first.copy(url = "file:///upload/1.png")) + other),
            input[1].copy(parts = listOf(repeated.copy(url = "file:///upload/2.png"))),
        ), output)
        other.forEachIndexed { index, part -> assertSame(part, output[0].parts[index + 1]) }
        assertEquals(emptyList(), Base64ImageToLocalFileTransformer.onGenerationFinish(context, emptyList()))
        assertEquals(2, received.size)
    }

    @Test
    fun malformedBase64FailsBeforeWritingOrProcessingLaterImages() = runTest {
        var writes = 0
        bind(Base64ImageStore { writes++; "file:///unexpected.png" })
        assertFailsWith<IllegalArgumentException> {
            Base64ImageToLocalFileTransformer.onGenerationFinish(context, listOf(
                UIMessage.user("unused").copy(parts = listOf(
                    UIMessagePart.Image("data:image/png;base64,!invalid!"), image(1),
                )),
            ))
        }
        assertEquals(0, writes)
    }

    @Test
    fun fileFailurePropagatesAndStopsTheFinishPipelineWithoutRetryingEarlierWrites() = runTest {
        val error = IOException("write failed")
        val writes = mutableListOf<Int>()
        bind(Base64ImageStore { bytes ->
            writes += bytes.single().toInt()
            if (writes.size == 2) throw error
            "file:///upload/first.png"
        })
        var reachedNextTransformer = false
        val next = object : OutputMessageTransformer {
            override suspend fun onGenerationFinish(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
                reachedNextTransformer = true
                return messages
            }
        }
        val failure = assertFailsWith<IOException> {
            listOf(UIMessage.user("unused").copy(parts = listOf(image(1), image(2), image(3))))
                .onGenerationFinish(
                    listOf(Base64ImageToLocalFileTransformer, next), context.model, context.assistant, context.settings,
                )
        }
        assertEquals(error.message, failure.message)
        assertEquals(listOf(1, 2), writes)
        assertFalse(reachedNextTransformer)
    }

    @Test
    fun cancellationPropagatesWithoutConvertingTheRemainingImages() = runTest {
        val cancellation = CancellationException("cancel conversion")
        var writes = 0
        bind(Base64ImageStore { writes++; throw cancellation })
        val failure = assertFailsWith<CancellationException> {
            Base64ImageToLocalFileTransformer.onGenerationFinish(context, listOf(
                UIMessage.user("unused").copy(parts = listOf(image(1), image(2))),
            ))
        }
        assertEquals(cancellation.message, failure.message)
        assertEquals(1, writes)
    }

    private fun image(value: Int) = UIMessagePart.Image(
        "data:image/png;base64," + Base64.encode(byteArrayOf(value.toByte())),
    )

    private fun bind(store: Base64ImageStore) {
        startKoin { modules(module { single<Base64ImageStore> { store } }) }
    }
}
