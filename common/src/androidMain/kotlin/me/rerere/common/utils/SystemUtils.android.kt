package me.rerere.common.utils

actual fun exitProcess(status: Int) {
    kotlin.system.exitProcess(status)
}
