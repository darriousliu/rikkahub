package me.rerere.rikkahub.ui.pages.imggen

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.compressImage
import io.github.vinceglb.filekit.readBytes

internal actual suspend fun readReferenceImage(source: PlatformFile): ByteArray = FileKit.compressImage(
    bytes = source.readBytes(),
    imageFormat = ImageFormat.PNG,
    quality = 100,
    maxWidth = 2048,
    maxHeight = 2048,
)
