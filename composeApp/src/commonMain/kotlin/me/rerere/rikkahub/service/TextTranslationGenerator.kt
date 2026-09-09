package me.rerere.rikkahub.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.utils.applyPlaceholders

/** Shared request construction and response handling for text translation. */
class TextTranslationGenerator(
    private val providerManager: ProviderManager,
) {
    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguageCode: String,
        targetLanguageName: String,
        onStreamUpdate: ((String) -> Unit)? = null,
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")
        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguageCode,
            )
            var messages = listOf(UIMessage.user(prompt))

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies,
                ),
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk)
                messages.lastOrNull()
                    ?.takeIf { it.role == MessageRole.ASSISTANT }
                    ?.toText()
                    ?.let { emitTranslation(it, onStreamUpdate) }
            }
        } else {
            val chunk = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(sourceText)),
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies + CustomBody(
                        key = "translation_options",
                        value = buildJsonObject {
                            put("source_lang", JsonPrimitive("auto"))
                            put("target_lang", JsonPrimitive(targetLanguageName))
                        },
                    ),
                ),
            )
            emitTranslation(chunk.choices.firstOrNull()?.message?.toText().orEmpty(), onStreamUpdate)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.emitTranslation(
        text: String,
        onStreamUpdate: ((String) -> Unit)?,
    ) {
        if (text.isNotBlank()) {
            onStreamUpdate?.invoke(text)
            emit(text)
        }
    }
}
