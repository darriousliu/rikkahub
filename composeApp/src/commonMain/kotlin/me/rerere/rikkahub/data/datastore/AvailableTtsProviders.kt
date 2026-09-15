package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.shared.isLinux
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.uuid.Uuid

/** Keep unavailable providers in storage so a backup still works on another platform. */
fun Settings.getAvailableTTSProviders(isLinux: Boolean = currentPlatformKind.isLinux): List<TTSProviderSetting> =
    if (isLinux) ttsProviders.filterNot { it is TTSProviderSetting.SystemTTS } else ttsProviders

fun Settings.getSelectedAvailableTTSProvider(isLinux: Boolean = currentPlatformKind.isLinux): TTSProviderSetting? =
    copy(ttsProviders = getAvailableTTSProviders(isLinux)).getSelectedTTSProvider()

internal fun availableTtsProviderTypes(isLinux: Boolean = currentPlatformKind.isLinux) =
    TTSProviderSetting.Types.filterNot { isLinux && it == TTSProviderSetting.SystemTTS::class }

internal fun newTtsProvider(isLinux: Boolean = currentPlatformKind.isLinux): TTSProviderSetting =
    if (isLinux) TTSProviderSetting.OpenAI() else TTSProviderSetting.SystemTTS()

internal fun Settings.moveTtsProvider(fromId: Uuid, toId: Uuid): List<TTSProviderSetting> =
    ttsProviders.toMutableList().apply {
        add(indexOfFirst { it.id == toId }, removeAt(indexOfFirst { it.id == fromId }))
    }
