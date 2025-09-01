package me.rerere.common.utils

expect object ThreadManager {
    fun runInBackground(task: () -> Unit)
    fun shutdown()
}
