package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import com.dokar.sonner.ToastType
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.encodeBase64
import me.rerere.common.time.toDashedFileTimestamp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Image02
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.chat_page_export_format
import me.rerere.rikkahub.generated.resources.chat_page_export_image
import me.rerere.rikkahub.generated.resources.chat_page_export_image_desc
import me.rerere.rikkahub.generated.resources.chat_page_export_image_expand_reasoning
import me.rerere.rikkahub.generated.resources.chat_page_export_markdown
import me.rerere.rikkahub.generated.resources.chat_page_export_markdown_desc
import me.rerere.rikkahub.generated.resources.chat_page_export_success
import me.rerere.rikkahub.generated.resources.mermaid_export
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.toLocalizedDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun ChatExportSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    conversation: Conversation,
    selectedMessages: List<UIMessage>
) {
    val context = LocalPlatformContext.current
    val compositionLocalContext = currentCompositionLocalContext
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val settings = LocalSettings.current
    var imageExportOptions by remember { mutableStateOf(ImageExportOptions()) }

    var pendingMarkdown by remember { mutableStateOf<String?>(null) }
    val markdownSuccessMessage = stringResource(Res.string.chat_page_export_success, "Markdown")
    val saveLauncher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
    ) { target ->
        val content = pendingMarkdown
        pendingMarkdown = null
        if (target != null && content != null) {
            scope.launch {
                try {
                    target.writeString(content)
                    toaster.show(markdownSuccessMessage, type = ToastType.Success)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stringResource(Res.string.chat_page_export_format))

                OutlinedCard(
                    onClick = {
                        pendingMarkdown = exportToMarkdown(conversation, selectedMessages)
                        saveLauncher.launch(
                            suggestedName = "chat-export-${Clock.System.now().toDashedFileTimestamp()}",
                            defaultExtension = "md",
                            allowedExtensions = setOf("md"),
                        )
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(Res.string.chat_page_export_markdown))
                        },
                        supportingContent = {
                            Text(stringResource(Res.string.chat_page_export_markdown_desc))
                        },
                        leadingContent = {
                            Icon(HugeIcons.File02, contentDescription = null)
                        }
                    )
                }

                val imageSuccessMessage =
                    stringResource(Res.string.chat_page_export_success, "Image")
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.chat_page_export_image))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.chat_page_export_image_desc))
                            },
                            leadingContent = {
                                Icon(HugeIcons.Image02, contentDescription = null)
                            }
                        )

                        HorizontalDivider()

                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.chat_page_export_image_expand_reasoning)) },
                            trailingContent = {
                                Switch(
                                    checked = imageExportOptions.expandReasoning,
                                    onCheckedChange = {
                                        imageExportOptions = imageExportOptions.copy(expandReasoning = it)
                                    }
                                )
                            }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            exportToImage(
                                                context = context,
                                                compositionLocalContext = compositionLocalContext,
                                                scope = scope,
                                                density = density,
                                                conversation = conversation,
                                                messages = selectedMessages,
                                                settings = settings,
                                                options = imageExportOptions
                                            )
                                        }.onSuccess {
                                            toaster.show(imageSuccessMessage, type = ToastType.Success)
                                        }.onFailure {
                                            if (it is CancellationException) throw it
                                            it.printStackTrace()
                                            toaster.show(
                                                message = "Failed to export image: ${it.message}",
                                                type = ToastType.Error
                                            )
                                        }
                                    }
                                    onDismissRequest()
                                }
                            ) {
                                Text(stringResource(Res.string.mermaid_export))
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun exportToMarkdown(
    conversation: Conversation,
    messages: List<UIMessage>
): String = buildAnnotatedString {
    append("# ${conversation.title}\n\n")
    append("*Exported on ${Clock.System.now().toLocalizedDateTime()}*\n\n")

    messages.forEach { message ->
        val role = if (message.role == MessageRole.USER) "**User**" else "**Assistant**"
        append("$role:\n\n")
        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    append(part.text)
                    appendLine()
                }

                is UIMessagePart.Image -> {
                    append("![Image](${part.encodeBase64().getOrNull()?.base64})")
                    appendLine()
                }

                is UIMessagePart.Reasoning -> {
                    part.reasoning.lines()
                        .filter { it.isNotBlank() }
                        .map { "> $it" }
                        .forEach {
                            append(it)
                        }
                    appendLine()
                    appendLine()
                }

                is UIMessagePart.Tool -> {
                    append("**Tool**: `${part.toolName}`")
                    appendLine()
                    if (part.toolCallId.isNotBlank()) {
                        append("- Call ID: `${part.toolCallId}`")
                        appendLine()
                    }

                    append("Input:")
                    appendLine()
                    append("```json")
                    appendLine()
                    append(JsonInstantPretty.encodeToString(part.inputAsJson()))
                    appendLine()
                    append("```")
                    appendLine()

                    if (part.output.isNotEmpty()) {
                        append("Output:")
                        appendLine()
                        part.output.forEach { outputPart ->
                            when (outputPart) {
                                is UIMessagePart.Text -> {
                                    append("```text")
                                    appendLine()
                                    append(outputPart.text)
                                    appendLine()
                                    append("```")
                                    appendLine()
                                }

                                is UIMessagePart.Reasoning -> {
                                    outputPart.reasoning.lines()
                                        .filter { it.isNotBlank() }
                                        .forEach {
                                            append("> $it")
                                            appendLine()
                                        }
                                }

                                is UIMessagePart.Image -> {
                                    append("![Tool Image](${outputPart.encodeBase64().getOrNull()?.base64})")
                                    appendLine()
                                }

                                is UIMessagePart.Document -> {
                                    append("[Document: ${outputPart.fileName}](${outputPart.url})")
                                    appendLine()
                                }

                                is UIMessagePart.Video -> {
                                    append("[Video](${outputPart.url})")
                                    appendLine()
                                }

                                is UIMessagePart.Audio -> {
                                    append("[Audio](${outputPart.url})")
                                    appendLine()
                                }

                                else -> {}
                            }
                        }
                    }
                    appendLine()
                }

                else -> {}
            }
        }
        appendLine()
        append("---")
        appendLine()
    }
}.toString()

data class ImageExportOptions(val expandReasoning: Boolean = false)

internal expect suspend fun exportToImage(
    context: PlatformContext,
    compositionLocalContext: CompositionLocalContext,
    scope: CoroutineScope,
    density: Density,
    conversation: Conversation,
    messages: List<UIMessage>,
    settings: Settings,
    options: ImageExportOptions,
)
