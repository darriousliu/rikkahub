package me.rerere.asr.providers

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import me.rerere.asr.ASRProviderSetting
import okhttp3.OkHttpClient

internal suspend fun transcribeMiMoAudio(
    httpClient: OkHttpClient,
    provider: ASRProviderSetting.MiMo,
    wavBytes: ByteArray,
): String {
    val client = httpClient.asKtorClient()
    return transcribeMiMoAudio(client, provider, wavBytes)
}

internal suspend fun transcribeStepAudio(
    httpClient: OkHttpClient,
    provider: ASRProviderSetting.Step,
    pcmBytes: ByteArray,
): String {
    val client = httpClient.asKtorClient()
    return transcribeStepAudio(client, provider, pcmBytes)
}

private fun OkHttpClient.asKtorClient(): HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = this@asKtorClient }
}
