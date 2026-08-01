package me.rerere.rikkahub.platform

import kotlin.system.exitProcess

actual fun terminateApplication() {
    exitProcess(0)
}
