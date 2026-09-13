package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.runtime.Composable

@Composable
expect fun WorkspacePage()

@Composable
expect fun WorkspaceDetailPage(id: String)

@Composable
expect fun WorkspaceTerminalPage(id: String)

@Composable
expect fun WorkspaceFileEditorPage(id: String, area: String, path: String)
