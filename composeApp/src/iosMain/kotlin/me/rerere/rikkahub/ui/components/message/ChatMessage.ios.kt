package me.rerere.rikkahub.ui.components.message

import coil3.PlatformContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.getCurrentViewController
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentInteractionController

internal actual fun openDocument(
    context: PlatformContext,
    part: UIMessagePart
) {
    val url = NSURL.fileURLWithPath(
        when (part) {
            is UIMessagePart.Video -> part.url
            is UIMessagePart.Audio -> part.url
            is UIMessagePart.Document -> part.url
            else -> return
        }
    )
    val fileName = if (part is UIMessagePart.Document) part.fileName else null

    // 获取当前活跃的 ViewController
    val currentViewController = getCurrentViewController()

    if (currentViewController != null) {
        val documentController = UIDocumentInteractionController.interactionControllerWithURL(url)
        documentController.name = fileName

        // 尝试预览
        val presented = documentController.presentPreviewAnimated(true)

        if (!presented) {
            // 预览失败，显示选项菜单
            documentController.presentOptionsMenuFromRect(
                rect = currentViewController.view.bounds,
                inView = currentViewController.view,
                animated = true
            )
        }
    }
}

