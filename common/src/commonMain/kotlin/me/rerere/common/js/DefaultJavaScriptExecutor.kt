package me.rerere.common.js

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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

    private suspend fun executeCancellable(request: JavaScriptExecutionRequest): JavaScriptExecution = try {
        withContext(runtimeDispatcher) { executeInRuntime(request) }
    } catch (error: Throwable) {
        currentCoroutineContext().ensureActive()
        if (error is QuickJsInterruptedException && request.timeoutMillis > 0) {
            throw JavaScriptTimeoutException(request.timeoutMillis)
        }
        throw error
    }

    private suspend fun executeInRuntime(request: JavaScriptExecutionRequest): JavaScriptExecution {
        val executionState = JavaScriptExecutionState()
        val console = mutableListOf<JavaScriptConsoleMessage>()
        val quickJs = QuickJs.create(jobDispatcher = runtimeDispatcher)
        try {
            runtimeObserver.onCreated()
            quickJs.evaluationTimeoutMillis = request.timeoutMillis
            val cancellationSignal = Job(currentCoroutineContext().job)
            cancellationSignal.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    executionState.cancelExecution()
                }
            }
            try {
                quickJs.evaluate<Any?>(
                    RUNTIME_BOOTSTRAP.completeWithUndefined(),
                    "runtime-bootstrap.js",
                )
                quickJs.installConsole(console)
                httpTransport?.let {
                    quickJs.installFetch(
                        transport = it,
                        executionState = executionState,
                        fetchDispatcher = fetchDispatcher,
                    )
                }
                request.setupScripts.forEachIndexed { index, script ->
                    quickJs.evaluate<Any?>(
                        code = script.completeWithUndefined(),
                        filename = "setup-$index.js",
                    )
                }

                val envelopeJson = quickJs.evaluate<String>(
                    code = evaluationEnvelopeScript(request.code),
                    filename = "execution.js",
                )
                val envelope = javaScriptJson.decodeFromString<JavaScriptEvaluationEnvelope>(envelopeJson)
                return JavaScriptExecution(
                    value = envelope.toJavaScriptValue(),
                    console = console.toList(),
                )
            } finally {
                cancellationSignal.complete()
            }
        } finally {
            executionState.cancelActiveCall()
            try {
                quickJs.close()
            } finally {
                runtimeObserver.onClosed()
            }
        }
    }

    private companion object {
        val runtimeDispatcher = Dispatchers.Default.limitedParallelism(4, "JavaScriptRuntime")
        val fetchDispatcher = javaScriptFetchDispatcher
    }
}

private suspend fun QuickJs.installConsole(console: MutableList<JavaScriptConsoleMessage>) {
    function<Unit>("__consoleWrite") { args ->
        val level = when (args.getOrNull(0) as? String) {
            "info" -> JavaScriptConsoleLevel.INFO
            "warn" -> JavaScriptConsoleLevel.WARN
            "error" -> JavaScriptConsoleLevel.ERROR
            else -> JavaScriptConsoleLevel.LOG
        }
        console += JavaScriptConsoleMessage(
            level = level,
            message = args.getOrNull(1) as? String,
        )
    }
    evaluate<Any?>(CONSOLE_POLYFILL.completeWithUndefined(), "console-polyfill.js")
}

private fun String.completeWithUndefined(): String = "$this\n;void 0;"

private fun evaluationEnvelopeScript(code: String): String {
    val encodedCode = javaScriptJson.encodeToString(code)
    return """
        (() => {
            const builtins = __rikkahubInternalJavaScriptBuiltins_7f3a;
            const value = (0, builtins.indirectEval)($encodedCode);
            if (value === null || value === undefined) {
                return builtins.jsonStringify({kind: "null"});
            }
            const type = typeof value;
            if (type === "object" || type === "function") {
                const json = builtins.jsonStringify(value);
                if (json === undefined) {
                    return builtins.jsonStringify({kind: "null"});
                }
                return builtins.jsonStringify({kind: "json", value: json});
            }
            return builtins.jsonStringify({kind: "scalar", value: builtins.toString(value)});
        })();
    """.trimIndent()
}

@Serializable
private data class JavaScriptEvaluationEnvelope(
    val kind: String,
    val value: String? = null,
)

private fun JavaScriptEvaluationEnvelope.toJavaScriptValue(): JavaScriptValue = when (kind) {
    "null" -> JavaScriptValue.Null
    "scalar" -> JavaScriptValue.Scalar(checkNotNull(value))
    "json" -> JavaScriptValue.Json(checkNotNull(value))
    else -> error("Unknown JavaScript result kind: $kind")
}

@OptIn(ExperimentalAtomicApi::class)
internal class JavaScriptExecutionState {
    private val cancelled = AtomicBoolean(false)
    private val activeCall = AtomicReference<JavaScriptHttpCall?>(null)

    fun install(call: JavaScriptHttpCall) {
        check(activeCall.compareAndSet(null, call)) { "Nested fetch calls are not supported" }
        if (cancelled.load()) {
            call.cancel()
            throw CancellationException("JavaScript execution was cancelled")
        }
    }

    fun clear(call: JavaScriptHttpCall) {
        activeCall.compareAndSet(call, null)
    }

    fun cancelExecution() {
        cancelled.store(true)
        cancelActiveCall()
    }

    fun cancelActiveCall() {
        activeCall.load()?.cancel()
    }
}

private const val CONSOLE_POLYFILL = """
(function() {
    const builtins = __rikkahubInternalJavaScriptBuiltins_7f3a;

    function format(value) {
        if (typeof value === 'string') return "'" + value + "'";
        if (value === null) return 'null';
        if (value === undefined) return 'undefined';
        if (typeof value === 'object') {
            try {
                return builtins.jsonStringify(value);
            } catch (_) {
                return builtins.toString(value);
            }
        }
        return builtins.toString(value);
    }

    function write(level, args) {
        __consoleWrite(level, builtins.arrayMap(args, format).join(', '));
    }

    globalThis.console = {
        log: function() { write('log', arguments); },
        debug: function() { write('log', arguments); },
        info: function() { write('info', arguments); },
        warn: function() { write('warn', arguments); },
        error: function() { write('error', arguments); }
    };
})();
"""

private const val RUNTIME_BOOTSTRAP = """
const __rikkahubInternalJavaScriptBuiltins_7f3a = (() => {
    const builtins = {
        indirectEval: (0, eval),
        jsonStringify: JSON.stringify.bind(JSON),
        toString: String,
        arrayMap: Function.prototype.call.bind(Array.prototype.map)
    };
    return Object.freeze(builtins);
})();
"""
