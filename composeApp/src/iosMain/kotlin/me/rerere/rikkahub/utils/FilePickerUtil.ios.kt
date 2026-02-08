package me.rerere.rikkahub.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import kotlin.coroutines.resume

@Composable
actual fun rememberFilePickerByMimeLauncher(
    onResult: (PlatformFile?) -> Unit
): FilePickerLauncher {
    val scope = rememberCoroutineScope()
    return remember {
        object : FilePickerLauncher {
            override fun launch(mimeTypes: Array<String>) {
                scope.launch {
                    val result = pickFileIOS(mimeTypes)
                    onResult(result)
                }
            }
        }
    }
}

private suspend fun pickFileIOS(mimeTypes: Array<String>): PlatformFile? =
    suspendCancellableCoroutine { cont ->
        val utTypes = mimeTypes.mapNotNull { mime ->
            mimeToUTType(mime)
        }.ifEmpty {
            listOf(UTTypeItem) // fallback: 所有文件
        }
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = utTypes
        )
        picker.allowsMultipleSelection = false
        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                if (url == null) {
                    cont.resume(null)
                    return
                }
                // 获取安全访问权限
                val accessed = url.startAccessingSecurityScopedResource()
                try {
                    val data = NSData.dataWithContentsOfURL(url)
                    if (data == null) {
                        cont.resume(null)
                        return
                    }
                    cont.resume(PlatformFile(url))
                } finally {
                    if (accessed) url.stopAccessingSecurityScopedResource()
                }
            }
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                cont.resume(null)
            }
        }
        picker.delegate = delegate
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVC?.presentViewController(picker, animated = true, completion = null)
        cont.invokeOnCancellation {
            picker.dismissViewControllerAnimated(true, completion = null)
        }
    }
/**
 * MIME -> UTType 转换
 */
private fun mimeToUTType(mime: String): UTType? {
    if (mime == "*/*") return UTTypeItem
    return UTType.typeWithMIMEType(mime)
}
