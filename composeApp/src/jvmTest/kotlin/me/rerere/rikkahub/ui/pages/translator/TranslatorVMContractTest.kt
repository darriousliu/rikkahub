package me.rerere.rikkahub.ui.pages.translator

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.TextTranslationGenerator
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TranslatorVMContractTest {
    private lateinit var ui: ExecutorCoroutineDispatcher
    private val fixtures = mutableListOf<Fixture>()

    @BeforeTest
    fun setUp() {
        ui = Executors.newSingleThreadExecutor { Thread(it, "translator-contract-ui") }.asCoroutineDispatcher()
        Dispatchers.setMain(ui)
    }

    @AfterTest
    fun tearDown() {
        runBlocking(ui) { fixtures.forEach { it.close() } }
        fixtures.clear()
        Dispatchers.resetMain()
        ui.close()
    }

    @Test
    fun `settings remain lazy and updates persist original translation keys`() = scenario {
        val f = fixture()
        assertTrue(f.vm.settings.value.init)
        f.observeSettings()
        val before = f.vm.settings.value
        val changed = before.copy(translatePrompt = "new {source_text} {target_lang}", translateThinkingBudget = 4096)
        f.vm.updateSettings(changed)
        assertEquals(before, f.vm.settings.value)
        f.vm.settings.first { it.translatePrompt == changed.translatePrompt }
        assertEquals(changed.translatePrompt, f.preferences.data.value[SettingsStore.TRANSLATION_PROMPT])
        assertEquals(changed.translateThinkingBudget, f.preferences.data.value[SettingsStore.TRANSLATE_THINKING_BUDGET])
        assertEquals(changed.translateModeId.toString(), f.preferences.data.value[SettingsStore.TRANSLATE_MODEL])
        assertEquals("translation_prompt", SettingsStore.TRANSLATION_PROMPT.name)
        assertEquals("translate_thinking_budget", SettingsStore.TRANSLATE_THINKING_BUDGET.name)
        assertEquals("translate_model", SettingsStore.TRANSLATE_MODEL.name)
        val reopened = SettingsStore(f.preferences, f.scope)
        assertEquals(changed.translateModeId, reopened.settingsFlow.value.translateModeId)
        assertEquals(changed.translatePrompt, reopened.settingsFlow.value.translatePrompt)
        assertEquals(changed.translateThinkingBudget, reopened.settingsFlow.value.translateThinkingBudget)
    }

    @Test
    fun `regular translation uses captured input current prompt and language and streams partial text`() = scenario {
        val f = fixture()
        f.observeSettings()
        f.vm.updateInputText("captured input")
        f.vm.translate()
        assertTrue(f.vm.translating.value)
        assertEquals("", f.vm.translatedText.value)
        f.vm.updateInputText("later input")
        f.vm.updateTargetLanguage(TranslationLanguage.Japanese)
        val call = f.provider.requests.receive()
        assertEquals("captured input -> ja", call.messages.single().toText())
        assertEquals("stream", call.kind)
        assertSame(Dispatchers.Default, call.dispatcher)
        assertEquals(ReasoningLevel.fromBudgetTokens(1024), call.params.reasoningLevel)
        call.reply(delta = "first")
        f.vm.translatedText.first { it == "first" }
        assertTrue(f.vm.translating.value)
        call.reply(delta = " second")
        f.vm.translatedText.first { it == "first second" }
        call.responses.close()
        f.vm.translating.first { !it }
        assertEquals("later input", f.vm.inputText.value)
        assertEquals("first second", f.vm.translatedText.value)
    }

    @Test
    fun `blank input does not cancel active work or clear its output`() = scenario {
        val f = fixture()
        f.observeSettings()
        f.vm.updateInputText("original")
        f.vm.translate()
        val call = f.provider.requests.receive()
        call.reply(delta = "partial")
        f.vm.translatedText.first { it == "partial" }
        f.vm.updateInputText(" \n\t")
        f.vm.translate()
        assertTrue(f.vm.translating.value)
        assertEquals("partial", f.vm.translatedText.value)
        assertFalse(call.finished.isCompleted)
        assertTrue(f.provider.requests.tryReceive().isFailure)
        call.reply(delta = " retained")
        f.vm.translatedText.first { it == "partial retained" }
        call.responses.close()
        f.vm.translating.first { !it }
    }

    @Test
    fun `qwen translation passes the language name and original non streaming options`() = scenario {
        val f = fixture(Model("qwen-mt-turbo", "Qwen MT"))
        f.observeSettings()
        f.vm.updateInputText("original")
        f.vm.updateTargetLanguage(TranslationLanguage.TraditionalChinese)
        f.vm.translate()
        val call = f.provider.requests.receive()
        assertEquals("generate", call.kind)
        assertEquals("original", call.messages.single().toText())
        assertEquals(0.3f, call.params.temperature)
        assertEquals(0.95f, call.params.topP)
        val options = call.params.customBody.single().value.jsonObject
        assertEquals("Chinese", options["target_lang"]!!.jsonPrimitive.content)
        assertEquals("auto", options["source_lang"]!!.jsonPrimitive.content)
        call.reply(message = "繁體結果")
        f.vm.translatedText.first { it == "繁體結果" }
        f.vm.translating.first { !it }
    }

    @Test
    fun `provider failure retains partial text exposes the same error and permits retry`() = scenario {
        val f = fixture()
        f.observeSettings()
        f.vm.updateInputText("original")
        f.vm.translate()
        val first = f.provider.requests.receive()
        first.reply(delta = "partial")
        f.vm.translatedText.first { it == "partial" }
        val failure = ProviderFailure("cmp10-request")
        val error = async(start = CoroutineStart.UNDISPATCHED) { f.vm.errorFlow.first() }
        first.responses.send(Result.failure(failure))
        assertSame(failure, error.await())
        f.vm.translating.first { !it }
        assertEquals("partial", f.vm.translatedText.value)
        f.vm.translate()
        assertEquals("", f.vm.translatedText.value)
        val retry = f.provider.requests.receive()
        retry.reply(delta = "recovered")
        retry.responses.close()
        f.vm.translating.first { !it }
        assertEquals("recovered", f.vm.translatedText.value)
    }

    @Test
    fun `missing model reports the existing error without a provider request`() = scenario {
        val f = fixture()
        f.store.update { it.copy(translateModeId = Uuid.random()) }
        f.observeSettings()
        val error = async(start = CoroutineStart.UNDISPATCHED) { f.vm.errorFlow.first() }
        f.vm.updateInputText("original")
        f.vm.translate()
        assertEquals("Translation model not found", assertIs<IllegalStateException>(error.await()).message)
        f.vm.translating.first { !it }
        assertTrue(f.provider.requests.tryReceive().isFailure)
        assertEquals("", f.vm.translatedText.value)
    }

    @Test
    fun `cancel stops the upstream request and preserves the partial result`() = scenario {
        val f = fixture()
        f.observeSettings()
        f.vm.updateInputText("original")
        f.vm.translate()
        val call = f.provider.requests.receive()
        call.reply(delta = "partial")
        f.vm.translatedText.first { it == "partial" }
        f.vm.cancelTranslation()
        assertFalse(f.vm.translating.value)
        call.finished.await()
        call.reply(delta = " must not appear")
        f.vm.cancelTranslation()
        assertEquals("partial", f.vm.translatedText.value)
        assertTrue(f.provider.requests.tryReceive().isFailure)
    }

    @Test
    fun `a new translation and clearing the VM cancel their previous upstream requests`() = scenario {
        val f = fixture()
        f.observeSettings()
        f.vm.updateInputText("first")
        f.vm.translate()
        val first = f.provider.requests.receive()
        first.reply(delta = "old")
        f.vm.translatedText.first { it == "old" }
        f.vm.updateInputText("second")
        f.vm.translate()
        assertEquals("", f.vm.translatedText.value)
        val second = f.provider.requests.receive()
        first.finished.await()
        assertEquals("second -> zh_CN", second.messages.single().toText())
        second.reply(delta = "new")
        f.vm.translatedText.first { it == "new" }
        f.viewModels.clear()
        second.finished.await()
        assertEquals("new", f.vm.translatedText.value)
    }

    @Test
    fun `native dispatcher wiring keeps Android IO and shared Default upstream contexts`() = scenario {
        for (dispatcher in listOf(Dispatchers.IO, Dispatchers.Default)) {
            val f = fixture(dispatcher = dispatcher)
            f.observeSettings()
            f.vm.updateInputText("original")
            f.vm.translate()
            val call = f.provider.requests.receive()
            assertSame(dispatcher, call.dispatcher)
            call.reply(delta = "result")
            call.responses.close()
            f.vm.translating.first { !it }
            assertEquals("result", f.vm.translatedText.value)
        }
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit) = runBlocking(ui) {
        withTimeout(10_000) { block() }
    }

    private suspend fun fixture(
        model: Model = Model("cmp10-regular", "Contract"),
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): Fixture = Fixture(dispatcher).also {
        fixtures += it
        it.store.update { settings ->
            settings.copy(
                providers = listOf(ProviderSetting.OpenAI(models = listOf(model))),
                translateModeId = model.id,
                translatePrompt = "{source_text} -> {target_lang}",
                translateThinkingBudget = 1024,
            )
        }
    }

    private class Fixture(dispatcher: CoroutineDispatcher) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val preferences = MemoryPreferences()
        val store = SettingsStore(preferences, scope)
        val provider = ControlledProvider()
        val client = HttpClient(MockEngine { error("Unexpected HTTP request") })
        val manager = ProviderManager(client).apply { registerProvider("openai", provider) }
        val viewModels = ViewModelStore()
        val vm by lazy {
            TranslatorVM(store, TextTranslationGenerator(manager), dispatcher).also { viewModels.put("translator", it) }
        }

        suspend fun observeSettings() {
            scope.launch { vm.settings.collect {} }
            vm.settings.first { !it.init }
        }

        suspend fun close() {
            viewModels.clear()
            scope.cancel()
            withTimeout(2_000) { provider.calls.forEach { it.finished.await() } }
            client.close()
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private class ProviderFailure(val requestId: String) : IllegalArgumentException("expected provider failure")

    private class Call(
        val kind: String,
        val messages: List<UIMessage>,
        val params: TextGenerationParams,
        val dispatcher: ContinuationInterceptor?,
    ) {
        val responses = Channel<Result<MessageChunk>>(Channel.UNLIMITED)
        val finished = CompletableDeferred<Unit>()
        suspend fun reply(delta: String? = null, message: String? = null) {
            responses.send(Result.success(MessageChunk(
                id = "contract", model = "fake", choices = listOf(
                    UIMessageChoice(0, delta?.let(UIMessage::assistant), message?.let(UIMessage::assistant), null),
                ),
            )))
        }
    }

    private class ControlledProvider : Provider<ProviderSetting.OpenAI> {
        val requests = Channel<Call>(Channel.UNLIMITED)
        val calls = ConcurrentLinkedQueue<Call>()

        override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> = emptyList()

        override suspend fun generateText(
            providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams,
        ): MessageChunk {
            val call = call("generate", messages, params)
            return try { call.responses.receive().getOrThrow() } finally { call.finished.complete(Unit) }
        }

        override suspend fun streamText(
            providerSetting: ProviderSetting.OpenAI, messages: List<UIMessage>, params: TextGenerationParams,
        ): Flow<MessageChunk> = flow {
            val call = call("stream", messages, params)
            try { for (response in call.responses) emit(response.getOrThrow()) }
            finally { call.finished.complete(Unit) }
        }

        private suspend fun call(kind: String, messages: List<UIMessage>, params: TextGenerationParams): Call =
            Call(kind, messages, params, currentCoroutineContext()[ContinuationInterceptor]).also {
                calls += it
                requests.send(it)
            }

        override suspend fun generateEmbedding(
            providerSetting: ProviderSetting.OpenAI, params: EmbeddingGenerationParams,
        ): EmbeddingGenerationResult = error("Unused")
    }
}
