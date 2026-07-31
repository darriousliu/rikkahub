package me.rerere.common.logging

import co.touchlab.kermit.Logger

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

private object KermitRikkaLogSink : RikkaLogSink {
    override fun write(record: RikkaLogRecord): Int {
        with(record) {
            when (level) {
                RikkaLogLevel.VERBOSE -> Logger.v(messageString = message, throwable = throwable, tag = tag)
                RikkaLogLevel.DEBUG -> Logger.d(messageString = message, throwable = throwable, tag = tag)
                RikkaLogLevel.INFO -> Logger.i(messageString = message, throwable = throwable, tag = tag)
                RikkaLogLevel.WARN -> Logger.w(messageString = message, throwable = throwable, tag = tag)
                RikkaLogLevel.ERROR -> Logger.e(messageString = message, throwable = throwable, tag = tag)
            }
        }
        return 0
    }
}

/** Android Log-compatible facade backed by Kermit on every supported platform. */
object RikkaLog {
    private val logger = RikkaLogger(KermitRikkaLogSink)

    fun v(tag: String, message: String, throwable: Throwable? = null): Int = logger.v(tag, message, throwable)

    fun d(tag: String, message: String, throwable: Throwable? = null): Int = logger.d(tag, message, throwable)

    fun i(tag: String, message: String, throwable: Throwable? = null): Int = logger.i(tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null): Int = logger.w(tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null): Int = logger.e(tag, message, throwable)
}
