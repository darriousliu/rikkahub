package me.rerere.common.utils

actual object RuntimeUtil {
    actual fun halt(status: Int) {
        Runtime.getRuntime().halt(status)
    }
}
