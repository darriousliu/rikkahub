package me.rerere.rikkahub.platform

import platform.posix.exit

actual fun terminateApplication() {
    exit(0)
}
