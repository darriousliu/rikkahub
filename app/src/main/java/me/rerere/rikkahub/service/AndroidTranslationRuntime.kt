package me.rerere.rikkahub.service

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.pages.translator.TranslationLanguage

class AndroidTranslationRuntime(
    private val settingsStore: SettingsStore,
    private val generationHandler: GenerationHandler,
) : TranslationRuntime {
    override val settingsFlow = settingsStore.settingsFlow

    override suspend fun updateSettings(settings: Settings) {
        settingsStore.update(settings)
    }

    override fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: TranslationLanguage,
        onStreamUpdate: (String) -> Unit,
    ): Flow<String> = generationHandler.translateText(
        settings = settings,
        sourceText = sourceText,
        targetLanguageCode = targetLanguage.promptCode,
        targetLanguageName = targetLanguage.apiName,
        onStreamUpdate = onStreamUpdate,
    )
}
