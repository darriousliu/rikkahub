package me.rerere.rikkahub.ui.pages.imggen

import io.github.vinceglb.filekit.PlatformFile

internal expect suspend fun readReferenceImage(source: PlatformFile): ByteArray
