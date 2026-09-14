@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package me.rerere.rikkahub.utils

import kotlin.native.getUnhandledExceptionHook
import kotlin.native.processUnhandledException
import kotlin.native.setUnhandledExceptionHook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.Foundation.NSThread
import platform.Foundation.NSUserDefaults

class CrashHandlerTest {
    @Test
    fun nativeHookPersistsBeforeForwardingAndClearsBothKeys() = withRestoredHandler {
        val failure = IllegalStateException("CMP75 crash 中文")
        var forwarded: Throwable? = null
        var persistedBeforeForwarding = false
        setUnhandledExceptionHook { throwable ->
            forwarded = throwable
            persistedBeforeForwarding = CrashHandler.hasCrashed() &&
                NSUserDefaults(suiteName = SUITE).stringForKey("stacktrace") == expectedReport(failure)
        }
        CrashHandler.install()
        processUnhandledException(failure)
        assertEquals(failure, forwarded)
        assertTrue(persistedBeforeForwarding)
        CrashHandler.clearCrashed()
        assertFalse(CrashHandler.hasCrashed())
        assertNull(CrashHandler.getStackTrace())
    }

    @Test
    fun reportKeepsOriginalFirst8000Characters() = withRestoredHandler {
        val failure = IllegalArgumentException("文".repeat(9000))
        setUnhandledExceptionHook { }
        CrashHandler.install()
        processUnhandledException(failure)
        assertEquals(expectedReport(failure).take(8000), CrashHandler.getStackTrace())
    }

    private fun expectedReport(failure: Throwable): String {
        val threadName = NSThread.currentThread.name ?: NSThread.currentThread.description.orEmpty()
        return "Thread: $threadName\n${failure.stackTraceToString()}\n"
    }

    private fun withRestoredHandler(block: () -> Unit) {
        val originalHandler = getUnhandledExceptionHook()
        val preferences = NSUserDefaults(suiteName = SUITE)
        val crashed = preferences.objectForKey("crashed")
        val stackTrace = preferences.objectForKey("stacktrace")
        try {
            CrashHandler.clearCrashed()
            assertFalse(CrashHandler.hasCrashed())
            assertNull(CrashHandler.getStackTrace())
            block()
        } finally {
            setUnhandledExceptionHook(originalHandler)
            preferences.setObject(crashed, forKey = "crashed")
            preferences.setObject(stackTrace, forKey = "stacktrace")
            preferences.synchronize()
        }
    }

    private companion object {
        const val SUITE = "me.rerere.rikkahub.crash_handler"
    }
}
