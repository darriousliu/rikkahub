package me.rerere.rikkahub

import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import me.rerere.rikkahub.shared.PlatformBuildInfo
import me.rerere.rikkahub.shared.PlatformRouteContent
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantExtensionsPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantLocalToolPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantPromptPage
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import me.rerere.rikkahub.ui.pages.debug.DebugPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceDetailPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceFileEditorPage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspacePage
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalPage
import me.rerere.rikkahub.ui.pages.log.LogPage
import me.rerere.rikkahub.ui.pages.search.SearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
import me.rerere.rikkahub.ui.pages.setting.SettingDonatePage
import me.rerere.rikkahub.ui.pages.setting.SettingFilesPage
import me.rerere.rikkahub.ui.pages.setting.SettingPreferencesUIPage
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerPage
import me.rerere.rikkahub.ui.pages.webview.WebViewPage
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

internal object AndroidPlatformRouteContent : PlatformRouteContent {
    @Composable
    override fun Render(screen: Screen) {
        when (screen) {
            is Screen.Chat -> ChatPage(
                id = Uuid.parse(screen.id),
                text = screen.text,
                files = screen.files.map { it.toUri() },
                nodeId = screen.nodeId?.let { Uuid.parse(it) },
            )

            is Screen.ShareHandler -> ShareHandlerPage(screen.text, screen.streamUri)
            is Screen.AssistantPrompt -> AssistantPromptPage(screen.id)
            is Screen.AssistantLocalTool -> AssistantLocalToolPage(screen.id)
            is Screen.AssistantInjections -> AssistantExtensionsPage(screen.id)
            is Screen.WebView -> WebViewPage(screen.url, screen.contentId)
            Screen.SettingPreferencesUI -> SettingPreferencesUIPage()
            Screen.SettingAbout -> SettingAboutPage(koinInject<PlatformBuildInfo>())
            Screen.SettingDonate -> SettingDonatePage()
            Screen.SettingFiles -> SettingFilesPage()
            Screen.Debug -> DebugPage()
            Screen.Log -> LogPage()
            Screen.Workspaces -> WorkspacePage()
            is Screen.WorkspaceDetail -> WorkspaceDetailPage(screen.id)
            is Screen.WorkspaceTerminal -> WorkspaceTerminalPage(screen.id)
            is Screen.WorkspaceFileEditor -> WorkspaceFileEditorPage(
                id = screen.id,
                area = WorkspaceStorageArea.valueOf(screen.area),
                path = screen.path,
            )

            Screen.MessageSearch -> SearchPage()
            else -> error("Route ${screen::class.simpleName} belongs to the shared navigation host")
        }
    }
}
