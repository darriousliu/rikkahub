package me.rerere.common.utils

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

actual object ThreadManager {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    actual fun runInBackground(task: () -> Unit) {
        executor.submit {
            task()
        }
    }

    actual fun shutdown() {
        executor.shutdown()
    }
}
