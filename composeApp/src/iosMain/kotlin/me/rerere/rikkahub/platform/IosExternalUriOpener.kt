package me.rerere.rikkahub.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

public class IosExternalUriOpener : ExternalUriOpener {
    override fun open(uri: String): Result<Unit> = runCatching {
        val url = NSURL.URLWithString(uri) ?: error("Invalid URI: $uri")
        // UIKit completes asynchronously; success here means the request was submitted.
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }
}
