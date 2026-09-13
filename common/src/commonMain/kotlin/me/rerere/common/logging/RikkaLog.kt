package me.rerere.common.logging

import co.touchlab.kermit.Logger

/** Android Log-compatible facade backed by Kermit on every supported platform. */
object RikkaLog {
    fun v(tag: String, message: String, throwable: Throwable? = null): Int {
        Logger.v(messageString = message, throwable = throwable, tag = tag)
        return 0
    }

    fun d(tag: String, message: String, throwable: Throwable? = null): Int {
        Logger.d(messageString = message, throwable = throwable, tag = tag)
        return 0
    }

    fun i(tag: String, message: String, throwable: Throwable? = null): Int {
        Logger.i(messageString = message, throwable = throwable, tag = tag)
        return 0
    }

    fun w(tag: String, message: String, throwable: Throwable? = null): Int {
        Logger.w(messageString = message, throwable = throwable, tag = tag)
        return 0
    }

    fun e(tag: String, message: String, throwable: Throwable? = null): Int {
        Logger.e(messageString = message, throwable = throwable, tag = tag)
        return 0
    }
}
