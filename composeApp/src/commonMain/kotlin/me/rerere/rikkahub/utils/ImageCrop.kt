package me.rerere.rikkahub.utils

import io.github.vinceglb.filekit.PlatformFile

/** Creates a decoder-compatible temporary copy, or returns null to use the source. */
internal expect fun prepareImageForCrop(source: PlatformFile): PlatformFile?
