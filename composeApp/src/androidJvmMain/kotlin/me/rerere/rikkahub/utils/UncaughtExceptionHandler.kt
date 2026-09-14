package me.rerere.rikkahub.utils

import me.rerere.common.logging.RikkaLog as Log

fun installUncaughtExceptionHandler(markCrashed: (Thread, Throwable) -> Unit) {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        Log.e("CrashHandler", "Uncaught exception on thread ${thread.name}", throwable)
        markCrashed(thread, throwable)
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
