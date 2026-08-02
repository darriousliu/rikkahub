package me.rerere.rikkahub.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.ui.pages.translator.TranslationLanguage
import me.rerere.rikkahub.utils.applyPlaceholders

internal class SharedTranslationRuntime(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
) : TranslationRuntime {
    override val settingsFlow: Flow<Settings> = settingsStore.settingsFlow

    override suspend fun updateSettings(settings: Settings) = settingsStore.update(settings)

    override fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: TranslationLanguage,
        onStreamUpdate: (String) -> Unit,
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")
        val providerHandler = providerManager.getProviderByType(provider)

        if (ModelRegistry.QWEN_MT.match(model.modelId)) {
            val chunk = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(sourceText)),
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customBody = listOf(
                        CustomBody(
                            key = "translation_options",
                            value = buildJsonObject {
                                put("source_lang", JsonPrimitive("auto"))
                                put("target_lang", JsonPrimitive(targetLanguage.apiName))
                            },
                        ),
                    ),
                ),
            )
            val translatedText = chunk.choices.firstOrNull()?.message?.toText().orEmpty()
            emitTranslation(translatedText, onStreamUpdate)
        } else {
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.promptCode,
            )
            var messages = listOf(UIMessage.user(prompt))

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                ),
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk)
                emitTranslation(messages.lastOrNull()?.toText().orEmpty(), onStreamUpdate)
            }
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.emitTranslation(
        text: String,
        onStreamUpdate: (String) -> Unit,
    ) {
        if (text.isNotBlank()) {
            onStreamUpdate(text)
            emit(text)
        }
    }
}
