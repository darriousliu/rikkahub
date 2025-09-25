package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import me.rerere.common.PlatformContext
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal actual val PlatformContext.settingsStore by lazy {
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath("settings").toPath() }
    )
}

internal fun producePath(fileName: String): String {
    val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory).path + "/datastore/$fileName.preferences_pb"
}
