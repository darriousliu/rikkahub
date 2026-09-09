package me.rerere.rikkahub.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TextTranslationGeneratorTest {
    private val clients = mutableListOf<HttpClient>()

    @AfterTest
    fun closeClients() {
        clients.forEach { it.close() }
        clients.clear()
    }

    @Test
    fun `regular translation substitutes prompt and uses original request parameters`() = runTest {
        val fixture = fixture(
            model = Model("regular", "Regular"),
            streamResponses = listOf(response(delta = "translated")),
            prompt = "text={source_text}; target={target_lang}",
            thinkingBudget = 4096,
        )

        val result = fixture.translate().toList()

        assertEquals(listOf("translated"), result)
        assertEquals("text=original; target=zh", fixture.provider.lastMessages.single().toText())
        assertEquals(ReasoningLevel.fromBudgetTokens(4096), fixture.provider.lastParams?.reasoningLevel)
    }

    @Test
    fun `streaming keeps original empty chunk handling and accumulates deltas`() = runTest {
        val fixture = fixture(
            streamResponses = listOf(
                response(),
                response(delta = "one"),
                response(delta = " two"),
            ),
        )
        val callbacks = mutableListOf<String>()

        val result = fixture.translate(onStreamUpdate = callbacks::add).toList()

        assertEquals(listOf("original -> zh", "one", "one two"), result)
        assertEquals(result, callbacks)
    }

    @Test
    fun `regular blank output is skipped while initial empty choices retain the latest message`() = runTest {
        val fixture = fixture(streamResponses = listOf(response(), response(delta = "   ")))

        assertEquals(listOf("original -> zh"), fixture.translate().toList())
        val blankOnly = fixture(streamResponses = listOf(response(delta = "   ")))
        assertEquals(emptyList(), blankOnly.translate().toList())
    }

    @Test
    fun `missing translation model fails without a provider request`() = runTest {
        val fixture = fixture(translationModelId = Uuid.random())

        assertFailsWith<IllegalStateException> { fixture.translate().toList() }
        assertEquals(0, fixture.provider.streamCalls)
        assertEquals(0, fixture.provider.generateCalls)
    }

    @Test
    fun `model provider override is used`() = runTest {
        val override = ProviderSetting.OpenAI(name = "Override", baseUrl = "https://override.invalid")
        val fixture = fixture(model = Model("regular", "Regular", providerOverwrite = override))

        fixture.translate().toList()

        assertEquals("Override", fixture.provider.lastSetting?.name)
        assertTrue(fixture.provider.lastSetting?.models?.isEmpty() == true)
    }

    @Test
    fun `provider failures and cancellation propagate`() = runTest {
        val failure = IllegalStateException("failed")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            fixture(providerFailure = failure).translate().toList()
        })
        assertIs<CancellationException>(assertFailsWith<CancellationException> {
            fixture(providerFailure = CancellationException("cancelled")).translate().toList()
        })
    }

    @Test
    fun `qwen mt uses original non streaming translation options`() = runTest {
        val fixture = fixture(
            model = Model("qwen-mt-turbo", "Qwen MT"),
            generatedResponse = response(message = "翻译"),
        )

        val result = fixture.translate(targetCode = "ignored", targetName = "Chinese").toList()

        assertEquals(listOf("翻译"), result)
        assertEquals(0, fixture.provider.streamCalls)
        assertEquals(1, fixture.provider.generateCalls)
        assertEquals(0.3f, fixture.provider.lastParams?.temperature)
        assertEquals(0.95f, fixture.provider.lastParams?.topP)
        assertEquals(1, fixture.provider.lastParams?.customBody?.size)
        val options = fixture.provider.lastParams!!.customBody.single().value.jsonObject
        assertEquals("auto", options["source_lang"]?.jsonPrimitive?.content)
        assertEquals("Chinese", options["target_lang"]?.jsonPrimitive?.content)
        assertEquals("original", fixture.provider.lastMessages.single().toText())
    }

    @Test
    fun `qwen empty choices and blank message emit nothing`() = runTest {
        listOf(response(), response(message = " ")).forEach { response ->
            val fixture = fixture(
                model = Model("qwen-mt", "Qwen MT"),
                generatedResponse = response,
            )
            assertEquals(emptyList(), fixture.translate().toList())
        }
    }

    private fun fixture(
        model: Model = Model("regular", "Regular"),
        translationModelId: Uuid = model.id,
        streamResponses: List<MessageChunk> = listOf(response(delta = "translation")),
        generatedResponse: MessageChunk = response(message = "translation"),
        providerFailure: Throwable? = null,
        prompt: String = "{source_text} -> {target_lang}",
        thinkingBudget: Int = 0,
    ): Fixture {
        val provider = FakeProvider(streamResponses, generatedResponse, providerFailure)
        val setting = ProviderSetting.OpenAI(models = listOf(model))
        val client = HttpClient(MockEngine { error("Unexpected HTTP request") }).also(clients::add)
        val manager = ProviderManager(client).apply { registerProvider("openai", provider) }
        return Fixture(
            provider = provider,
            generator = TextTranslationGenerator(manager),
            settings = Settings(
                providers = listOf(setting),
                translateModeId = translationModelId,
                translatePrompt = prompt,
                translateThinkingBudget = thinkingBudget,
            ),
        )
    }

    private class Fixture(
        val provider: FakeProvider,
        val generator: TextTranslationGenerator,
        val settings: Settings,
    ) {
        fun translate(
            targetCode: String = "zh",
            targetName: String = "Chinese",
            onStreamUpdate: ((String) -> Unit)? = null,
        ): Flow<String> = generator.translateText(settings, "original", targetCode, targetName, onStreamUpdate)
    }

    private class FakeProvider(
        private val streamResponses: List<MessageChunk>,
        private val generatedResponse: MessageChunk,
        private val failure: Throwable?,
    ) : Provider<ProviderSetting.OpenAI> {
        var streamCalls = 0
        var generateCalls = 0
        var lastSetting: ProviderSetting.OpenAI? = null
        var lastMessages: List<UIMessage> = emptyList()
        var lastParams: TextGenerationParams? = null

        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): MessageChunk {
            generateCalls++
            record(providerSetting, messages, params)
            failure?.let { throw it }
            return generatedResponse
        }

        override suspend fun streamText(
            providerSetting: ProviderSetting.OpenAI,
            messages: List<UIMessage>,
            params: TextGenerationParams,
        ): Flow<MessageChunk> = flow {
            streamCalls++
            record(providerSetting, messages, params)
            failure?.let { throw it }
            streamResponses.forEach { emit(it) }
        }

        override suspend fun generateEmbedding(
            providerSetting: ProviderSetting.OpenAI,
            params: EmbeddingGenerationParams,
        ): EmbeddingGenerationResult = error("Unused")

        private fun record(setting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams) {
            lastSetting = setting
            lastMessages = messages
            lastParams = params
        }
    }

    private companion object {
        fun response(delta: String? = null, message: String? = null): MessageChunk = MessageChunk(
            id = "response",
            model = "fake",
            choices = when {
                delta != null || message != null -> listOf(
                    UIMessageChoice(0, delta?.let(UIMessage::assistant), message?.let(UIMessage::assistant), "stop"),
                )
                else -> emptyList()
            },
        )
    }
}
