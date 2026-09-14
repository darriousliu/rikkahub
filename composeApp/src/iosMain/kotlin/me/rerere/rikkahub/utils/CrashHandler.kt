@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package me.rerere.rikkahub.utils

import kotlin.native.getUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException
import platform.Foundation.NSThread
import platform.Foundation.NSUserDefaults
import me.rerere.common.logging.RikkaLog as Log

object CrashHandler {
    private val preferences = NSUserDefaults(suiteName = "me.rerere.rikkahub.crash_handler")

    fun install() {
        val defaultHandler = getUnhandledExceptionHook()
        setUnhandledExceptionHook { throwable ->
            val threadName = NSThread.currentThread.name ?: NSThread.currentThread.description.orEmpty()
            Log.e("CrashHandler", "Uncaught exception on thread $threadName", throwable)
            markCrashed(threadName, throwable)
            if (defaultHandler != null) {
                defaultHandler(throwable)
            } else {
                terminateWithUnhandledException(throwable)
            }
        }
    }

    fun hasCrashed(): Boolean = preferences.boolForKey("crashed")

    fun getStackTrace(): String? = preferences.stringForKey("stacktrace")

    fun clearCrashed() {
        preferences.removeObjectForKey("crashed")
        preferences.removeObjectForKey("stacktrace")
        preferences.synchronize()
    }

    private fun markCrashed(threadName: String, throwable: Throwable) {
        val stackTrace = buildString {
            appendLine("Thread: $threadName")
            appendLine(throwable.stackTraceToString())
        }.take(8000)
        preferences.setBool(true, forKey = "crashed")
        preferences.setObject(stackTrace, forKey = "stacktrace")
        preferences.synchronize()
    }
}
