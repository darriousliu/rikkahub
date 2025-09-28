package me.rerere.rikkahub.ui.components.message

import coil3.PlatformContext
import me.rerere.ai.ui.UIMessagePart
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController

internal actual fun openDocument(
    context: PlatformContext,
    document: UIMessagePart.Document
) {
    val url = NSURL.fileURLWithPath(document.url)

    // 获取当前活跃的 ViewController
    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
    val currentViewController = getCurrentViewController(rootViewController)

    if (currentViewController != null) {
        val documentController = UIDocumentInteractionController.interactionControllerWithURL(url)
        documentController.name = document.fileName

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

// 辅助函数：获取当前显示的 ViewController
private fun getCurrentViewController(viewController: UIViewController?): UIViewController? {
    if (viewController == null) return null

    return when {
        viewController.presentedViewController != null -> {
            getCurrentViewController(viewController.presentedViewController)
        }
        viewController is UINavigationController -> {
            getCurrentViewController(viewController.topViewController)
        }
        viewController is UITabBarController -> {
            getCurrentViewController(viewController.selectedViewController)
        }
        else -> viewController
    }
}
