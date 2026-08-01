package me.rerere.rikkahub.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

public class IosExternalUriOpener : ExternalUriOpener {
    override fun open(uri: String): Result<Unit> = runCatching {
        val url = NSURL.URLWithString(uri) ?: error("Invalid URI: $uri")
        check(UIApplication.sharedApplication.openURL(url)) { "No application can open URI: $uri" }
    }
}
