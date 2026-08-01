package me.rerere.tts.provider.providers

import kotlin.io.encoding.Base64

private val audioBase64 = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

internal fun decodeAudioBase64(data: String): ByteArray =
    audioBase64.decode(data.filterNot(Char::isWhitespace))
