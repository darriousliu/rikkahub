package me.rerere.rikkahub.utils

import platform.UIKit.UIApplication
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController


internal fun getCurrentViewController(): UIViewController? {
    val keyWindow = UIApplication.sharedApplication.keyWindow
    return keyWindow?.rootViewController?.let { findTopViewController(it) }
}

internal fun findTopViewController(controller: UIViewController?): UIViewController? {
    if (controller == null) return null
    return when {
        controller.presentedViewController != null ->
            findTopViewController(controller.presentedViewController)

        controller is UINavigationController && controller.topViewController != null ->
            findTopViewController(controller.topViewController)

        controller is UITabBarController && controller.selectedViewController != null ->
            findTopViewController(controller.selectedViewController)

        else -> controller
    }
}
