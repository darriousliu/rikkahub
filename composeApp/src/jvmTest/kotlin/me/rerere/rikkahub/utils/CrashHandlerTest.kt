package me.rerere.rikkahub.utils

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashHandlerTest {
    @Test
    fun uncaughtExceptionIsPersistedBeforeForwardingAndClearedOnRecovery() = withIsolatedHandler {
        val failure = IllegalStateException("CMP75 crash 中文")
        var forwarded: Throwable? = null
        var persistedBeforeForwarding = false
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            forwarded = throwable
            persistedBeforeForwarding = CrashHandler.hasCrashed() &&
                CrashHandler.getStackTrace() == "Thread: ${thread.name}\n${failure.stackTraceToString()}\n"
        }
        CrashHandler.install()
        val thread = Thread({ throw failure }, "CMP75-worker")
        thread.start()
        thread.join()
        assertEquals(failure, forwarded)
        assertTrue(persistedBeforeForwarding)
        assertTrue(CrashHandler.hasCrashed())
        CrashHandler.clearCrashed()
        assertFalse(CrashHandler.hasCrashed())
        assertNull(CrashHandler.getStackTrace())
    }

    @Test
    fun reportKeepsOriginalFirst8000Characters() = withIsolatedHandler {
        val failure = IllegalArgumentException("文".repeat(9000))
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        CrashHandler.install()
        val thread = Thread.currentThread()
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(thread, failure)
        assertEquals(
            "Thread: ${thread.name}\n${failure.stackTraceToString()}\n".take(8000),
            CrashHandler.getStackTrace(),
        )
    }

    private fun withIsolatedHandler(block: () -> Unit) {
        val originalHome = System.getProperty("user.home")
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val directory = Files.createTempDirectory("cmp75-crash-handler").toFile()
        try {
            System.setProperty("user.home", directory.absolutePath)
            assertFalse(CrashHandler.hasCrashed())
            assertNull(CrashHandler.getStackTrace())
            block()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            System.setProperty("user.home", originalHome)
            directory.deleteRecursively()
        }
    }
}
