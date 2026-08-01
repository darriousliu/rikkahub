package me.rerere.rikkahub.data.ai.mcp

import android.content.Context
import me.rerere.rikkahub.platform.AndroidExternalUriOpener

/** OAuth 授权回调的 redirect_uri，需与 AndroidManifest 中 McpOAuthCallbackActivity 的 intent-filter 保持一致。 */
const val MCP_OAUTH_REDIRECT_URI = "rikkahub://mcp-oauth-callback"

/** 使用 Chrome Custom Tabs 打开授权 URL。 */
fun launchOAuthAuthorization(context: Context, authorizationUrl: String) {
    AndroidExternalUriOpener(context).open(authorizationUrl).getOrThrow()
}
