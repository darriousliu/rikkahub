package me.rerere.common.utils

expect class NetworkRequestManager() {
    suspend fun <T> executeWithBackgroundProtection(
        operation: suspend () -> T
    ): T
}
