package me.rerere.rikkahub.data.ai.transformers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import korlibs.template.KorteTemplateProvider
import korlibs.template.KorteTemplates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.shared.template.createMessageTemplateEngine
import me.rerere.rikkahub.utils.JsonInstant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TemplateTransformerContractTest {
    @Test
    fun preservesMessagesAndNonTextPartsWhileRenderingEveryTextPart() = runTest {
        val f = Fixture(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            "{{ role }}|{{ message }}")
        val metadata = JsonObject(mapOf("test" to JsonPrimitive("unchanged")))
        val image = UIMessagePart.Image("file:///fixture.png", metadata)
        val first = UIMessagePart.Text("<b>Hello & 世界</b>", metadata)
        val second = UIMessagePart.Text("", metadata)
        val messages = MessageRole.entries.map { role ->
            message("unused", role).copy(
                parts = listOf(first, image, second),
                finishedAt = LocalDateTime(2021, 1, 3, 4, 5, 6),
                translation = "keep translation",
            )
        }
        val result = f.transformer.transform(f.context(), messages)
        assertEquals(messages.map { it.copy(parts = emptyList()) }, result.map { it.copy(parts = emptyList()) })
        result.forEachIndexed { index, rendered ->
            val role = messages[index].role.name.lowercase()
            assertEquals(first.copy(text = role + "|" + first.text), rendered.parts[0])
            assertSame(image, rendered.parts[1])
            assertEquals(second.copy(text = role + "|"), rendered.parts[2])
        }
        assertEquals(listOf(f.assistant.id.toString()), f.reads)
    }

    @Test
    fun usesMessageTimeAndLocalizedOriginalFormatsAcrossLocalesAndTimeZones() = runTest {
        val previousLocale = Locale.getDefault()
        val previousZone = TimeZone.getDefault()
        try {
            for (locale in listOf(Locale.US, Locale.CHINA)) {
                Locale.setDefault(locale)
                for (zone in listOf("UTC", "America/New_York", "Asia/Shanghai")) {
                    TimeZone.setDefault(TimeZone.getTimeZone(zone))
                    val f = Fixture(
                        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
                        "{{ time }}|{{ date }}|{{ role }}|{{ message }}",
                    )
                    val local = java.time.LocalDateTime.of(2021, 1, 3, 4, 5, 6)
                    val expected = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale)
                        .format(local) + "|" +
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(local) +
                        "|user|fixed"
                    val input = message("fixed")
                    assertEquals(expected, f.render(input))
                    assertEquals(expected, f.render(input))
                    assertEquals(1, f.reads.size)
                }
            }
        } finally {
            Locale.setDefault(previousLocale)
            TimeZone.setDefault(previousZone)
        }
    }

    @Test
    fun capturesConversionTimeZoneButFormatsInTheCurrentSystemZone() = runTest {
        val previousLocale = Locale.getDefault()
        val previousZone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val f = Fixture(
                CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
                "{{ time }}",
            )
            f.renderer.config.replaceVariablePocessor { name, previous ->
                TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
                previous(name)
            }
            // The original conversion uses New York's DST gap, while each formatter reads the current zone.
            val input = message("first").copy(
                createdAt = LocalDateTime(2024, 3, 10, 2, 30),
                parts = listOf(UIMessagePart.Text("first"), UIMessagePart.Text("second")),
            )
            val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.US)
            val first = formatter.format(java.time.LocalTime.of(3, 30))
            val afterZoneChange = formatter.format(java.time.LocalTime.of(7, 30))
            val result = f.transformer.transform(f.context(), listOf(input, input))
            assertEquals(
                listOf(first, afterZoneChange, afterZoneChange, afterZoneChange),
                result.flatMap { it.parts }.map { (it as UIMessagePart.Text).text },
            )
        } finally {
            Locale.setDefault(previousLocale)
            TimeZone.setDefault(previousZone)
        }
    }

    @Test
    fun selectsStoredAssistantTemplateByIdInsteadOfContextTemplateCopy() = runTest {
        val f = Fixture(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            "stored:{{ message }}")
        assertEquals("stored:one", f.render(message("one"), f.assistant.copy(messageTemplate = "wrong")))
        assertEquals("second:two", f.render(message("two"), f.second))
        assertEquals(listOf(f.assistant.id.toString(), f.second.id.toString()), f.reads)
    }

    @Test
    fun stillLoadsTemplateForEmptyInputAndPropagatesMissingAssistant() = runTest {
        val f = Fixture(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        assertEquals(emptyList(), f.transformer.transform(f.context(), emptyList()))
        assertEquals(1, f.reads.size)
        val missing = Assistant()
        assertEquals(missing.id.toString(), assertFailsWith<KorteTemplateProvider.NotFoundException> {
            f.transformer.transform(f.context(missing), emptyList())
        }.template)
    }

    @Test
    fun retainsCompiledTemplateUntilCacheInvalidation() = runTest {
        val f = Fixture(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            "before:{{ message }}")
        assertEquals("before:a", f.render(message("a")))
        f.changeSourceWithoutNotification("after:{{ message }}")
        assertEquals("before:b", f.render(message("b")))
        assertEquals(1, f.reads.size)
        f.renderer.invalidateCache()
        assertEquals("after:c", f.render(message("c")))
        assertEquals(2, f.reads.size)
    }

    @Test
    fun settingsStoreChangeInvalidatesAndPersistsTemplateUnderOriginalKey() = runTest {
        val f = Fixture(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            "before")
        assertEquals("before", f.render(message("x")))
        f.store.update { settings ->
            settings.copy(assistants = settings.assistants.map {
                if (it.id == f.assistant.id) it.copy(messageTemplate = "after:{{ message }}") else it
            })
        }
        assertEquals("after:x", f.render(message("x")))
        val persisted = JsonInstant.decodeFromString<List<Assistant>>(
            f.preferences.data.value[SettingsStore.ASSISTANTS]!!,
        )
        assertEquals("after:{{ message }}", persisted.first { it.id == f.assistant.id }.messageTemplate)
        assertEquals("assistants", SettingsStore.ASSISTANTS.name)
        f.store.update { it.copy(dynamicColor = !it.dynamicColor) }
        assertEquals("after:x", f.render(message("x")))
        assertEquals(3, f.reads.size)
    }

    @Test
    fun failedCompilationCanRetryWithoutCachingTheFailure() = runTest {
        val f = Fixture(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            "{% unsupported_tag %}")
        assertFailsWith<Throwable> { f.render(message("x")) }
        f.changeSourceWithoutNotification("recovered:{{ message }}")
        assertEquals("recovered:x", f.render(message("x")))
        assertEquals(2, f.reads.size)
    }

    @Test
    fun renderingFailureDoesNotEvictAnAlreadyCompiledTemplate() = runTest {
        val f = Fixture(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            "{{ message|join(',') }}")
        val first = assertFailsWith<Throwable> { f.render(message("x")) }
        assertTrue(first.message.orEmpty().contains("expects a collection or array"))
        f.changeSourceWithoutNotification("recovered")
        assertFailsWith<Throwable> { f.render(message("x")) }
        assertEquals(1, f.reads.size)
        f.renderer.invalidateCache()
        assertEquals("recovered", f.render(message("x")))
    }

    @Test
    fun providerFailureAndCancellationPropagateAndDoNotPoisonTheCache() = runTest {
        for (error in listOf(IllegalStateException("source failed"), CancellationException("source cancelled"))) {
            val f = Fixture(
                CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            )
            f.failure = error
            assertSame(error, assertFailsWith<Throwable> { f.render(message("x")) })
            f.failure = null
            assertEquals("x", f.render(message("x")))
        }
    }

    @Test
    fun includesAndLayoutsUseTheSameAssistantSourceAndContext() = runTest {
        val f = Fixture(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        f.changeSourceWithoutNotification("{% include '" + f.second.id + "' %}|{{ message }}")
        assertEquals("second:x|x", f.render(message("x")))
        f.changeSourceWithoutNotification(
            "{% extends '" + f.second.id + "' %}{% block body %}child:{{ message }}{% endblock %}"
        )
        f.changeSourceWithoutNotification("base[{% block body %}default{% endblock %}]", f.second)
        f.renderer.invalidateCache()
        assertEquals("base[child:x]", f.render(message("x")))
    }

    @Test
    fun preservesPebbleCompatibleLoopsAndFilters() = runTest {
        val f = Fixture(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        val cases = listOf(
            "{% for x in missing %}bad{% else %}bad{% endfor %}OK" to "OK",
            "{% for x in [] %}bad{% else %}empty{% endfor %}" to "empty",
            "{% for x in ['a','b'] %}{{ loop.index }}:{{ loop.revindex }}:{{ x }};{% endfor %}" to "0:1:a;1:0:b;",
            "{{ '  '|default('blank') }}|{{ false|default('bad') }}|{{ 0|default('bad') }}" to "blank|false|0",
            "{{ ['a','b']|join('-') }}|{{ ['a','b']|join }}|{{ missing|join }}" to "a-b|ab|",
            "{{ '  hello WORLD'|capitalize }}|{{ 'aBc'|upper }}|{{ 'aBc'|lower }}" to "  Hello WORLD|ABC|abc",
            "{{ 'banana'|replace({'a':'o'}) }}" to "bonono",
            "{{ missing }}|{{ message }}" to "|<raw>&",
        )
        for ((template, expected) in cases) {
            f.changeSourceWithoutNotification(template)
            f.renderer.invalidateCache()
            assertEquals(expected, f.render(message("<raw>&")), template)
        }
    }

    private class Fixture(scope: CoroutineScope, template: String = "{{ message }}") {
        val assistant = Assistant(name = "contract", messageTemplate = template)
        val second = Assistant(name = "second", messageTemplate = "second:{{ message }}")
        val reads = mutableListOf<String>()
        var failure: Throwable? = null
        val renderer: KorteTemplates = createMessageTemplateEngine()
        val preferences = MemoryPreferences(preferencesOf(
            SettingsStore.ASSISTANTS to JsonInstant.encodeToString(listOf(assistant, second)),
        ))
        val store: SettingsStore = SettingsStore(preferences, scope, onSettingsChanged = renderer::invalidateCache)
        private val loader = AssistantTemplateLoader(store)
        val transformer = TemplateTransformer(renderer.apply {
            val provider = object : KorteTemplateProvider {
                override suspend fun get(template: String): String? {
                    reads += template
                    failure?.let { throw it }
                    return loader.get(template)
                }
            }
            root = provider
            includes = provider
            layouts = provider
        })

        fun context(assistant: Assistant = this.assistant) =
            TransformerContext(Model(modelId = "gpt-4o", displayName = "GPT-4o"), assistant, store.settingsFlow.value)

        suspend fun render(message: UIMessage, assistant: Assistant = this.assistant): String =
            transformer.transform(context(assistant), listOf(message)).single().toText()

        fun changeSourceWithoutNotification(template: String, assistant: Assistant = this.assistant) {
            store.settingsFlow.value = store.settingsFlow.value.copy(
                assistants = store.settingsFlow.value.assistants.map {
                    if (it.id == assistant.id) it.copy(messageTemplate = template) else it
                },
            )
        }
    }

    private class MemoryPreferences(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    companion object {
        private fun message(text: String, role: MessageRole = MessageRole.USER) = UIMessage(
            role = role,
            parts = listOf(UIMessagePart.Text(text)),
            createdAt = LocalDateTime(2021, 1, 3, 4, 5, 6),
        )
    }
}
