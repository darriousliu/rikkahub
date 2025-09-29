package me.rerere.common.utils

import co.touchlab.kermit.Logger
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

actual class NetworkRequestManager {
    private var backgroundTaskId: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid

    actual suspend fun <T> executeWithBackgroundProtection(operation: suspend () -> T): T {
        Logger.i("NetworkRequestManager") { "Execute network request in background protection" }
        // 开始后台任务
        backgroundTaskId = UIApplication.sharedApplication.beginBackgroundTaskWithName("Chat Completion") {
            Logger.i("NetworkRequestManager") { "Background task expired" }
            endBackgroundTask()
        }

        return try {
            operation()
        } finally {
            endBackgroundTask()
        }
    }

    private fun endBackgroundTask() {
        Logger.i("NetworkRequestManager") { "End background task: $backgroundTaskId" }
        UIApplication.sharedApplication.endBackgroundTask(backgroundTaskId)
        backgroundTaskId = UIBackgroundTaskInvalid
    }
}
