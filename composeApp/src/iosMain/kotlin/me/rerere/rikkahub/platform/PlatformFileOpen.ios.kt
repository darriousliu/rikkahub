package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.compose.koinInject
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@Composable
@OptIn(ExperimentalForeignApi::class)
internal actual fun rememberFileOpener(): (String) -> Result<Unit> {
    val opener = koinInject<ExternalUriOpener>()
    val viewController = LocalUIViewController.current
    // UIDocumentInteractionController only holds a weak reference to its delegate.
    val delegate = remember(viewController) {
        object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
            override fun documentInteractionControllerViewControllerForPreview(
                controller: UIDocumentInteractionController,
            ): UIViewController = viewController
        }
    }
    val documentController = remember(delegate) {
        UIDocumentInteractionController().apply { this.delegate = delegate }
    }
    DisposableEffect(documentController) {
        onDispose {
            documentController.dismissPreviewAnimated(false)
            documentController.dismissMenuAnimated(false)
            documentController.delegate = null
        }
    }
    return remember(opener, documentController, viewController) {
        { uri ->
            val url = NSURL.URLWithString(uri)
            if (url?.fileURL == true) {
                runCatching {
                    // FileKit 0.15 still calls the deprecated openURL: here; use native preview for local files.
                    documentController.URL = url
                    val opened = documentController.presentPreviewAnimated(true) ||
                        documentController.presentOptionsMenuFromRect(
                            rect = viewController.view.bounds,
                            inView = viewController.view,
                            animated = true,
                        )
                    check(opened) { "No application can open URI: $uri" }
                }
            } else {
                opener.open(uri)
            }
        }
    }
}
