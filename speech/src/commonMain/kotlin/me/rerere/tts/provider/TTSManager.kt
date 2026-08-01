package me.rerere.tts.provider

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.providers.ElevenLabsTTSProvider
import me.rerere.tts.provider.providers.FishAudioTTSProvider
import me.rerere.tts.provider.providers.GeminiTTSProvider
import me.rerere.tts.provider.providers.GroqTTSProvider
import me.rerere.tts.provider.providers.MiMoTTSProvider
import me.rerere.tts.provider.providers.MiniMaxTTSProvider
import me.rerere.tts.provider.providers.OpenAITTSProvider
import me.rerere.tts.provider.providers.QwenTTSProvider
import me.rerere.tts.provider.providers.StepTTSProvider
import me.rerere.tts.provider.providers.XAITTSProvider

class TTSManager(
    httpClient: HttpClient,
    private val systemProvider: TTSProvider<TTSProviderSetting.SystemTTS>,
) {
    private val openAIProvider = OpenAITTSProvider(httpClient)
    private val geminiProvider = GeminiTTSProvider(httpClient)
    private val miniMaxProvider = MiniMaxTTSProvider(httpClient)
    private val qwenProvider = QwenTTSProvider(httpClient)
    private val groqProvider = GroqTTSProvider(httpClient)
    private val xaiProvider = XAITTSProvider(httpClient)
    private val miMoProvider = MiMoTTSProvider(httpClient)
    private val stepProvider = StepTTSProvider(httpClient)
    private val elevenLabsProvider = ElevenLabsTTSProvider(httpClient)
    private val fishAudioProvider = FishAudioTTSProvider(httpClient)

    fun generateSpeech(
        providerSetting: TTSProviderSetting,
        request: TTSRequest
    ): Flow<AudioChunk> {
        return when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.Gemini -> geminiProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.SystemTTS -> systemProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.MiniMax -> miniMaxProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.Qwen -> qwenProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.Groq -> groqProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.XAI -> xaiProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.MiMo -> miMoProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.FishAudio -> fishAudioProvider.generateSpeech(providerSetting, request)
            is TTSProviderSetting.Step -> stepProvider.generateSpeech(providerSetting, request)
        }
    }

    /**
     * 返回该 provider 硬编码的语气标记引导提示词（默认空）。
     * 供 text_to_speech 工具注入 system prompt 使用。
     */
    fun getPromptGuidance(providerSetting: TTSProviderSetting): String {
        return when (providerSetting) {
            is TTSProviderSetting.OpenAI -> openAIProvider.promptGuidance
            is TTSProviderSetting.Gemini -> geminiProvider.promptGuidance
            is TTSProviderSetting.SystemTTS -> systemProvider.promptGuidance
            is TTSProviderSetting.MiniMax -> miniMaxProvider.promptGuidance
            is TTSProviderSetting.Qwen -> qwenProvider.promptGuidance
            is TTSProviderSetting.Groq -> groqProvider.promptGuidance
            is TTSProviderSetting.XAI -> xaiProvider.promptGuidance
            is TTSProviderSetting.MiMo -> miMoProvider.promptGuidance
            is TTSProviderSetting.ElevenLabs -> elevenLabsProvider.promptGuidance
            is TTSProviderSetting.FishAudio -> fishAudioProvider.promptGuidance
            is TTSProviderSetting.Step -> stepProvider.promptGuidance
        }
    }
}
