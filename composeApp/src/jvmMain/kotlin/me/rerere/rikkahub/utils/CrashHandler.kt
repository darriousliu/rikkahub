package me.rerere.rikkahub.utils

import java.io.File
import java.util.Properties

object CrashHandler {
    private val preferencesFile: File
        get() = File(System.getProperty("user.home"), ".rikkahub/crash_handler.properties")

    fun install() {
        installUncaughtExceptionHandler(::markCrashed)
    }

    fun hasCrashed(): Boolean = readPreferences().getProperty("crashed").toBoolean()

    fun getStackTrace(): String? = readPreferences().getProperty("stacktrace")

    fun clearCrashed() {
        preferencesFile.delete()
    }

    private fun readPreferences(): Properties = Properties().apply {
        if (preferencesFile.exists()) {
            preferencesFile.inputStream().use(::load)
        }
    }

    private fun markCrashed(thread: Thread, throwable: Throwable) {
        val stackTrace = buildString {
            appendLine("Thread: ${thread.name}")
            appendLine(throwable.stackTraceToString())
        }.take(8000)
        val preferences = Properties().apply {
            setProperty("crashed", "true")
            setProperty("stacktrace", stackTrace)
        }
        preferencesFile.parentFile.mkdirs()
        preferencesFile.outputStream().use { output ->
            preferences.store(output, null)
            output.fd.sync()
        }
    }
}
