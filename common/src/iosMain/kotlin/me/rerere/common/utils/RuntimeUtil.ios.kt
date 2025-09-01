package me.rerere.common.utils

actual object RuntimeUtil {
    actual fun halt(status: Int) {
        exitProcess(status)
    }
}
