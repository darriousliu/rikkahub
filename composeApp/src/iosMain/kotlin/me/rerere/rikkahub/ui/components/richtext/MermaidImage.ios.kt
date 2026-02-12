package me.rerere.rikkahub.ui.components.richtext

import platform.Foundation.NSDictionary
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual class GlobalMermaidInterface actual constructor(
    actual val onRenderSuccess: (String, String, Int) -> Unit,
    actual val onRenderError: (String, String) -> Unit
) : (String) -> Unit {

    override fun invoke(message: String) {
        try {
            val nsString = message as NSString
            val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return
            val json = NSJSONSerialization.JSONObjectWithData(data, 0u, null) as? NSDictionary ?: return

            val method = json.objectForKey("method") as? String ?: return
            val id = json.objectForKey("id") as? String ?: return

            when (method) {
                "onRenderSuccess" -> {
                    val base64 = json.objectForKey("base64") as? String ?: ""
                    val height = (json.objectForKey("height") as? Number)?.toInt() ?: 0
                    onRenderSuccess(id, base64, height)
                }
                "onRenderError" -> {
                    val error = json.objectForKey("error") as? String ?: ""
                    onRenderError(id, error)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
