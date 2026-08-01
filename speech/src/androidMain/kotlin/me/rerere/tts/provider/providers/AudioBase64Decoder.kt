package me.rerere.tts.provider.providers

internal fun decodeAudioBase64(data: String): ByteArray =
    android.util.Base64.decode(data, android.util.Base64.DEFAULT)
