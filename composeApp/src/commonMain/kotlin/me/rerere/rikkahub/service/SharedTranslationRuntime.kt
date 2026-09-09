package me.rerere.rikkahub.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.pages.translator.TranslationLanguage

internal class SharedTranslationRuntime(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
) : TranslationRuntime {
    private val translationGenerator = TextTranslationGenerator(providerManager)

    override val settingsFlow: Flow<Settings> = settingsStore.settingsFlow

    override suspend fun updateSettings(settings: Settings) = settingsStore.update(settings)

    override fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: TranslationLanguage,
        onStreamUpdate: (String) -> Unit,
    ): Flow<String> = translationGenerator.translateText(
        settings = settings,
        sourceText = sourceText,
        targetLanguageCode = targetLanguage.promptCode,
        targetLanguageName = targetLanguage.apiName,
        onStreamUpdate = onStreamUpdate,
    ).flowOn(Dispatchers.Default)
}
