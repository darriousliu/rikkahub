package me.rerere.rikkahub.utils

import coil3.request.ImageRequest
import coil3.request.allowHardware

actual fun ImageRequest.Builder.platformAllowHardware(enable: Boolean): ImageRequest.Builder {
    return allowHardware(enable)
}
