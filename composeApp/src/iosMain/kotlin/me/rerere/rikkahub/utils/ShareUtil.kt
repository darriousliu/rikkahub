package me.rerere.rikkahub.utils

import kotlinx.cinterop.useContents
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIDevice
import platform.UIKit.UIModalPresentationOverFullScreen
import platform.UIKit.UIModalPresentationPopover
import platform.UIKit.UIPopoverArrowDirectionAny
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.popoverPresentationController
import rikkahub.composeapp.generated.resources.*

object ShareUtil {
    fun shareText(
        shareText: String,
        shareTitle: String? = null,
        noShareApp: String? = null
    ) {
        val currentViewController = getCurrentViewController()
        if (currentViewController == null) {
            // 无法获取当前 ViewController，可以考虑使用其他方式或忽略
            return
        }
        val activityViewController = UIActivityViewController(
            activityItems = listOf(shareText),
            applicationActivities = null
        )
        val confirm = runBlocking { getString(Res.string.confirm) }

        // 确保模态呈现样式正确
        activityViewController.modalPresentationStyle = UIModalPresentationOverFullScreen

        // 检查设备类型
        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            // iPad 特殊处理 - 使用 popover
            activityViewController.modalPresentationStyle = UIModalPresentationPopover
            activityViewController.popoverPresentationController?.apply {
                sourceView = currentViewController.view
                sourceRect = CGRectMake(
                    currentViewController.view.bounds.useContents { size.width } / 2.0,
                    currentViewController.view.bounds.useContents { size.height } / 2.0,
                    0.0,
                    0.0
                )
                permittedArrowDirections = UIPopoverArrowDirectionAny
            }
        } else {
            // iPhone 使用全屏模态
            activityViewController.modalPresentationStyle = UIModalPresentationOverFullScreen
        }

        // 设置完成回调
        activityViewController.completionWithItemsHandler = { _, completed, _, error ->
            if (error != null && !completed) {
                val alert = UIAlertController.alertControllerWithTitle(
                    title = shareTitle,
                    message = noShareApp,
                    preferredStyle = UIAlertControllerStyleAlert
                )
                alert.addAction(
                    UIAlertAction.actionWithTitle(
                        title = confirm,
                        style = UIAlertActionStyleDefault,
                        handler = null
                    )
                )
                currentViewController.presentViewController(alert, animated = true, completion = null)
            }
        }

        currentViewController.presentViewController(activityViewController, animated = true, completion = null)
    }

    fun shareFile(
        shareUri: String,
        shareTitle: String? = null,
        noShareApp: String? = null
    ) {
        val viewController = getCurrentViewController() ?: return
        val fileURL = NSURL(string = shareUri)

        // 创建 UIActivityViewController
        val activityItems = listOf(fileURL)
        val activityViewController = UIActivityViewController(
            activityItems = activityItems,
            applicationActivities = null
        )

        // 确保模态呈现样式正确
        activityViewController.modalPresentationStyle = UIModalPresentationOverFullScreen

        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            // iPad 需要设置 popover
            activityViewController.popoverPresentationController?.let { popover ->
                popover.sourceView = viewController.view
                popover.sourceRect = CGRectMake(
                    x = viewController.view.bounds.useContents { size.width / 2 },
                    y = viewController.view.bounds.useContents { size.height / 2 },
                    width = 0.0,
                    height = 0.0
                )
            }
        }

        // 设置完成回调
        activityViewController.completionWithItemsHandler = { _, completed, _, error ->
            if (error != null && !completed) {
                val alert = UIAlertController.alertControllerWithTitle(
                    title = shareTitle,
                    message = noShareApp,
                    preferredStyle = UIAlertControllerStyleAlert
                )
                alert.addAction(
                    UIAlertAction.actionWithTitle(
                        title = null,
                        style = UIAlertActionStyleDefault,
                        handler = null
                    )
                )
                viewController.presentViewController(alert, animated = true, completion = null)
            }
        }

        // 显示分享界面
        viewController.presentViewController(
            viewControllerToPresent = activityViewController,
            animated = true,
            completion = null
        )
    }
}
