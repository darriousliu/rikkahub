package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.runtime.Composable
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.UnavailableRoute

@Composable
actual fun WorkspacePage() = UnavailableRoute(Screen.Workspaces)

@Composable
actual fun WorkspaceDetailPage(id: String) = UnavailableRoute(Screen.WorkspaceDetail(id))

@Composable
actual fun WorkspaceTerminalPage(id: String) = UnavailableRoute(Screen.WorkspaceTerminal(id))

@Composable
actual fun WorkspaceFileEditorPage(id: String, area: String, path: String) =
    UnavailableRoute(Screen.WorkspaceFileEditor(id, area, path))
