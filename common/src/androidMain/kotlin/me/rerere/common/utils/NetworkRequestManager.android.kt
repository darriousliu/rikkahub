package me.rerere.common.utils

actual class NetworkRequestManager {
    actual suspend inline fun <T> executeWithBackgroundProtection(operation: suspend () -> T): T {
        return operation()
    }
}
