package me.rerere.rikkahub.ui.components.ui

import kotlin.io.encoding.Base64
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.utils.JsonInstant

private const val PROVIDER_SHARE_PREFIX = "ai-provider:v1:"

fun ProviderSetting.encodeForShare(): String {
    val json = JsonInstant.encodeToString(copyProvider(models = emptyList()))
    return PROVIDER_SHARE_PREFIX + Base64.encode(json.encodeToByteArray())
}

fun decodeProviderSetting(value: String): ProviderSetting {
    require(value.startsWith(PROVIDER_SHARE_PREFIX)) { "Invalid provider setting string" }
    val json = Base64.decode(value.removePrefix(PROVIDER_SHARE_PREFIX)).decodeToString()
    return JsonInstant.decodeFromString(json)
}
