package me.rerere.common.logging

import android.util.Log

internal enum class RikkaLogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}

internal data class RikkaLogRecord(
    val level: RikkaLogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable?
)

internal fun interface RikkaLogSink {
    fun write(record: RikkaLogRecord): Int
}

internal class RikkaLogger(
    private val sink: RikkaLogSink
) {
    fun v(tag: String, message: String, throwable: Throwable? = null): Int =
        write(RikkaLogLevel.VERBOSE, tag, message, throwable)

    fun d(tag: String, message: String, throwable: Throwable? = null): Int =
        write(RikkaLogLevel.DEBUG, tag, message, throwable)

    fun i(tag: String, message: String, throwable: Throwable? = null): Int =
        write(RikkaLogLevel.INFO, tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null): Int =
        write(RikkaLogLevel.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null): Int =
        write(RikkaLogLevel.ERROR, tag, message, throwable)

    private fun write(
        level: RikkaLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?
    ): Int = sink.write(RikkaLogRecord(level, tag, message, throwable))
}

private object AndroidRikkaLogSink : RikkaLogSink {
    override fun write(record: RikkaLogRecord): Int = with(record) {
        when (level) {
            RikkaLogLevel.VERBOSE -> if (throwable == null) Log.v(tag, message) else Log.v(tag, message, throwable)
            RikkaLogLevel.DEBUG -> if (throwable == null) Log.d(tag, message) else Log.d(tag, message, throwable)
            RikkaLogLevel.INFO -> if (throwable == null) Log.i(tag, message) else Log.i(tag, message, throwable)
            RikkaLogLevel.WARN -> if (throwable == null) Log.w(tag, message) else Log.w(tag, message, throwable)
            RikkaLogLevel.ERROR -> if (throwable == null) Log.e(tag, message) else Log.e(tag, message, throwable)
        }
    }
}

/**
 * Android Log-compatible facade used while logging call sites move into shared source sets.
 */
object RikkaLog {
    private val logger = RikkaLogger(AndroidRikkaLogSink)

    fun v(tag: String, message: String, throwable: Throwable? = null): Int = logger.v(tag, message, throwable)

    fun d(tag: String, message: String, throwable: Throwable? = null): Int = logger.d(tag, message, throwable)

    fun i(tag: String, message: String, throwable: Throwable? = null): Int = logger.i(tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null): Int = logger.w(tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null): Int = logger.e(tag, message, throwable)
}
