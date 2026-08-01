package me.rerere.rikkahub.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

@Composable
public actual fun rememberPlatformTextSharer(): TextSharer = remember {
    TextSharer { text ->
        runCatching {
            val rootController = checkNotNull(
                UIApplication.sharedApplication.keyWindow?.rootViewController,
            ) { "No view controller is available to present the share sheet" }
            val presenter = generateSequence(rootController) { it.presentedViewController }.last()
            val shareController = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null,
            )
            configurePopover(shareController, presenter)
            presenter.presentViewController(shareController, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun configurePopover(
    shareController: UIActivityViewController,
    presenter: platform.UIKit.UIViewController,
) {
    shareController.popoverPresentationController?.apply {
        sourceView = presenter.view
        sourceRect = presenter.view.bounds
    }
}
