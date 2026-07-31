package me.rerere.common.js

import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DefaultJavaScriptExecutor internal constructor(
    private val httpTransport: JavaScriptHttpTransport? = null,
    private val runtimeObserver: JavaScriptRuntimeObserver,
) : JavaScriptExecutor {
    constructor(httpTransport: JavaScriptHttpTransport? = null) : this(
        httpTransport = httpTransport,
        runtimeObserver = NoOpJavaScriptRuntimeObserver,
    )

    override suspend fun execute(request: JavaScriptExecutionRequest): JavaScriptExecution =
        if (request.timeoutMillis > 0) {
            withTimeoutOrNull(request.timeoutMillis) { executeCancellable(request) }
                ?: throw JavaScriptTimeoutException(request.timeoutMillis)
        } else {
            executeCancellable(request)
        }

    private suspend fun executeCancellable(request: JavaScriptExecutionRequest): JavaScriptExecution =
        suspendCancellableCoroutine { continuation ->
            val executionState = JavaScriptExecutionState()
            val worker = runtimeScope.launch {
                try {
                    val execution = executeBlocking(request, executionState)
                    if (continuation.isActive) {
                        continuation.resume(execution)
                    }
                } catch (error: Throwable) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
            }

            continuation.invokeOnCancellation {
                executionState.cancelExecution()
                worker.cancel()
            }
        }

    private fun executeBlocking(
        request: JavaScriptExecutionRequest,
        executionState: JavaScriptExecutionState,
    ): JavaScriptExecution {
        QuickJSLoader.init()
        val console = mutableListOf<JavaScriptConsoleMessage>()
        val context = QuickJSContext.create()
        try {
            runtimeObserver.onCreated()
            context.setConsole(object : QuickJSContext.Console {
                override fun log(info: String?) {
                    console += JavaScriptConsoleMessage(JavaScriptConsoleLevel.LOG, info)
                }

                override fun info(info: String?) {
                    console += JavaScriptConsoleMessage(JavaScriptConsoleLevel.INFO, info)
                }

                override fun warn(info: String?) {
                    console += JavaScriptConsoleMessage(JavaScriptConsoleLevel.WARN, info)
                }

                override fun error(info: String?) {
                    console += JavaScriptConsoleMessage(JavaScriptConsoleLevel.ERROR, info)
                }
            })
            httpTransport?.let { context.injectFetch(it, executionState, fetchDispatcher) }
            request.setupScripts.forEach(context::evaluate)

            val value = when (val result = context.evaluate(request.code)) {
                null -> JavaScriptValue.Null
                is QuickJSObject -> JavaScriptValue.Json(result.stringify())
                else -> JavaScriptValue.Scalar(result.toString())
            }
            return JavaScriptExecution(value = value, console = console.toList())
        } finally {
            executionState.cancelActiveCall()
            try {
                context.destroy()
            } finally {
                runtimeObserver.onClosed()
            }
        }
    }

    private companion object {
        val runtimeDispatcher = Dispatchers.Default.limitedParallelism(4, "JavaScriptRuntime")
        val runtimeScope = CoroutineScope(SupervisorJob() + runtimeDispatcher)
        val fetchDispatcher = Dispatchers.IO.limitedParallelism(8, "JavaScriptFetch")
    }
}

internal class JavaScriptExecutionState {
    private val cancelled = AtomicBoolean()
    private val activeCall = AtomicReference<JavaScriptHttpCall?>()

    fun install(call: JavaScriptHttpCall) {
        check(activeCall.compareAndSet(null, call)) { "Nested fetch calls are not supported" }
        if (cancelled.get()) {
            call.cancel()
            throw CancellationException("JavaScript execution was cancelled")
        }
    }

    fun clear(call: JavaScriptHttpCall) {
        activeCall.compareAndSet(call, null)
    }

    fun cancelExecution() {
        cancelled.set(true)
        cancelActiveCall()
    }

    fun cancelActiveCall() {
        activeCall.get()?.cancel()
    }
}
