package me.rerere.common.js

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val javaScriptFetchDispatcher: CoroutineDispatcher =
    Dispatchers.IO.limitedParallelism(8, "JavaScriptFetch")
