package me.rerere.rikkahub.ui.pages.translator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.TextTranslationGenerator

private const val TAG = "TranslatorVM"

class TranslatorVM(
    private val settingsStore: SettingsStore,
    private val translationGenerator: TextTranslationGenerator,
    private val translationDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    // 翻译状态
    private val _translating = MutableStateFlow(false)
    val translating: StateFlow<Boolean> = _translating

    // 输入文本
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    // 翻译结果
    private val _translatedText = MutableStateFlow("")
    val translatedText: StateFlow<String> = _translatedText

    // 翻译目标语言
    private val _targetLanguage = MutableStateFlow(TranslationLanguage.SimplifiedChinese)
    val targetLanguage: StateFlow<TranslationLanguage> = _targetLanguage

    // 错误流
    val errorFlow = MutableSharedFlow<Throwable>()

    // 当前任务
    private var currentJob: Job? = null

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun updateTargetLanguage(language: TranslationLanguage) {
        _targetLanguage.value = language
    }

    fun translate() {
        val inputText = _inputText.value
        if (inputText.isBlank()) return

        // 取消当前任务
        currentJob?.cancel()

        // 设置翻译中状态
        _translating.value = true
        _translatedText.value = ""

        currentJob = viewModelScope.launch {
            runCatching {
                val settings = settings.value
                val targetLanguage = targetLanguage.value
                translationGenerator.translateText(
                    settings = settings,
                    sourceText = inputText,
                    targetLanguageCode = targetLanguage.promptCode,
                    targetLanguageName = targetLanguage.apiName,
                ) { translatedText ->
                    // Update translation in real-time
                    _translatedText.value = translatedText
                }.flowOn(translationDispatcher)
                    .collect { /* Final translation already handled in onStreamUpdate */ }
            }.onFailure {
                it.printStackTrace()
                errorFlow.emit(it)
            }

            _translating.value = false
        }
    }

    fun cancelTranslation() {
        currentJob?.cancel()
        _translating.value = false
    }
}
