package me.rerere.rikkahub.service

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.pages.translator.TranslationLanguage

interface TranslationRuntime {
    val settingsFlow: Flow<Settings>

    suspend fun updateSettings(settings: Settings)

    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: TranslationLanguage,
        onStreamUpdate: (String) -> Unit,
    ): Flow<String>
}
