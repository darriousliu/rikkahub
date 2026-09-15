package me.rerere.asr.providers

internal expect fun gzipCompress(data: ByteArray): ByteArray
internal expect fun gzipDecompress(data: ByteArray): ByteArray
