package me.rerere.common.utils

import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue

actual object ThreadManager {

    private val backgroundQueue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)

    actual fun runInBackground(task: () -> Unit) {
        dispatch_async(backgroundQueue) {
            task()
        }
    }

    actual fun shutdown() {
    }
}
