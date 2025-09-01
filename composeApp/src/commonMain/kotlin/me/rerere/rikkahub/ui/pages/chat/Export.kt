package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import coil3.Uri
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.composables.icons.lucide.BookDashed
import com.composables.icons.lucide.BookHeart
import com.composables.icons.lucide.Earth
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wrench
import com.dokar.sonner.ToastType
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.write
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.ai.ui.toSortedMessageParts
import me.rerere.ai.util.encodeBase64
import me.rerere.common.PlatformContext
import me.rerere.common.android.appTempFolder
import me.rerere.common.utils.delete
import me.rerere.common.utils.getUriForFile
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.BitmapComposer
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.utils.platformAllowHardware
import me.rerere.rikkahub.utils.toLocalString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import rikkahub.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Composable
fun ChatExportSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    conversation: Conversation,
    selectedMessages: List<UIMessage>
) {
    val context = LocalPlatformContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val settings = LocalSettings.current
    var imageExportOptions by remember { mutableStateOf(ImageExportOptions()) }

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stringResource(Res.string.chat_page_export_format))

                val markdownSuccessMessage =
                    stringResource(Res.string.chat_page_export_success, "Markdown")
                OutlinedCard(
                    onClick = {
                        scope.launch {
                            exportToMarkdown(context, conversation, selectedMessages)
                            toaster.show(
                                markdownSuccessMessage,
                                type = ToastType.Success
                            )
                            onDismissRequest()
                        }
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
                            Icon(Lucide.FileText, contentDescription = null)
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
                                Icon(Lucide.Image, contentDescription = null)
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
                                                scope = scope,
                                                density = density,
                                                conversation = conversation,
                                                messages = selectedMessages,
                                                settings = settings,
                                                options = imageExportOptions
                                            )
                                        }.onFailure {
                                            it.printStackTrace()
                                            toaster.show(
                                                message = "Failed to export image: ${it.message}",
                                                type = ToastType.Error
                                            )
                                        }
                                    }
                                    toaster.show(
                                        imageSuccessMessage,
                                        type = ToastType.Success
                                    )
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

private suspend fun exportToMarkdown(
    context: PlatformContext,
    conversation: Conversation,
    messages: List<UIMessage>
) {
    val filename =
        "chat-export-${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toLocalString()}.md"

    val sb = buildAnnotatedString {
        append("# ${conversation.title}\n\n")
        append(
            "*Exported on ${
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toLocalString()
            }*\n\n"
        )

        messages.forEach { message ->
            val role = if (message.role == MessageRole.USER) "**User**" else "**Assistant**"
            append("$role:\n\n")
            message.parts.toSortedMessageParts().forEach { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        append(part.text)
                        appendLine()
                    }

                    is UIMessagePart.Image -> {
                        append("![Image](${part.encodeBase64().getOrNull()})")
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

                    else -> {}
                }
            }
            appendLine()
            append("---")
            appendLine()
        }
    }

    try {
        val dir = context.appTempFolder
        val file = dir.resolve(filename)
        if (!file.exists()) {
            file.write(byteArrayOf())
        } else {
            file.delete()
            file.write(byteArrayOf())
        }
        file.writeString(sb.toString())

        // Share the file
        val uri = getUriForFile(
            context,
            file
        )
        shareFile(context, uri, "text/markdown")

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

internal expect suspend fun exportToImage(
    context: PlatformContext,
    scope: CoroutineScope,
    density: Density,
    conversation: Conversation,
    messages: List<UIMessage>,
    settings: Settings,
    options: ImageExportOptions = ImageExportOptions()
)

data class ImageExportOptions(val expandReasoning: Boolean = false)

@Composable
internal fun ExportedChatImage(
    conversation: Conversation,
    messages: List<UIMessage>,
    options: ImageExportOptions = ImageExportOptions()
) {
    val navBackStack = rememberNavController()
    val highlighter = koinInject<Highlighter>()
    RikkahubTheme {
        CompositionLocalProvider(
            LocalNavController provides navBackStack,
            LocalHighlighter provides highlighter
        ) {
            Surface(
                modifier = Modifier.width(540.dp) // like 1080p but with density independence
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = conversation.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${
                                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toLocalString()
                                }  rikka-ai.com",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Use painterResource for the logo
                        val painter = painterResource(Res.drawable.ic_launcher_foreground)
                        Image(
                            painter = painter,
                            contentDescription = "Logo",
                            modifier = Modifier.size(60.dp)
                        )
                    }

                    // Messages
                    messages.forEach { message ->
                        ExportedChatMessage(
                            message = message,
                            options = options,
                            prevMessage = messages.getOrNull(messages.indexOf(message) - 1)
                        )
                    }

                    // Watermark
                    Column {
                        Text(
                            text = stringResource(Res.string.export_image_warning),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportedChatMessage(
    message: UIMessage,
    prevMessage: UIMessage? = null,
    options: ImageExportOptions = ImageExportOptions()
) {
    if (message.parts.isEmptyUIMessage()) return
    val context = LocalPlatformContext.current
    val settings = LocalSettings.current
    val model = message.modelId?.let { settings.findModelById(it) }
    // Always show model icon for assistant messages in exported images
    val showModelIcon = message.role == MessageRole.ASSISTANT && prevMessage?.role == MessageRole.USER
    val iconLabel = when {
        model?.modelId?.isNotBlank() == true -> model.modelId
        model?.displayName?.isNotBlank() == true -> model.displayName
        else -> "AI"
    }
    val messageContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .widthIn(max = (540 * 0.9).dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start
        ) {
            message.parts.toSortedMessageParts().forEach { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        if (part.text.isNotBlank()) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (message.role) {
                                        MessageRole.USER -> MaterialTheme.colorScheme.primaryContainer
                                        else -> Color.Transparent
                                    }
                                )
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                    MarkdownBlock(
                                        content = part.text,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    is UIMessagePart.Image -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(part.url)
                                .platformAllowHardware(false)
                                .crossfade(false)
                                .build(),
                            contentDescription = "Image",
                            modifier = Modifier
                                .sizeIn(maxHeight = 300.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }

                    is UIMessagePart.Reasoning -> {
                        ExportedReasoningCard(reasoning = part, expanded = options.expandReasoning)
                    }

                    is UIMessagePart.ToolCall -> {
                        ExportedToolCall(toolCall = part)
                    }

                    is UIMessagePart.ToolResult -> {
                        ExportedToolResult(toolResult = part)
                    }

                    else -> {
                        // Other parts are not rendered in image export for now
                    }
                }
            }
        }
    }

    if (showModelIcon) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            AutoAIIcon(
                name = iconLabel,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(36.dp)
            )

            Text(
                text = iconLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
    messageContent()
}

@Composable
private fun ExportedReasoningCard(reasoning: UIMessagePart.Reasoning, expanded: Boolean) {
    val duration = reasoning.finishedAt?.let { endTime ->
        endTime - reasoning.createdAt
    } ?: (Clock.System.now() - reasoning.createdAt)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.deepthink),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(Res.string.deep_thinking),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (duration > 0.seconds) {
                    Text(
                        text = "(${duration.toString(DurationUnit.SECONDS, 1)})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (expanded) {
                MarkdownBlock(
                    content = reasoning.reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ExportedToolCall(
    toolCall: UIMessagePart.ToolCall
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            Icon(
                imageVector = when (toolCall.toolName) {
                    "create_memory", "edit_memory" -> Lucide.BookHeart
                    "delete_memory" -> Lucide.BookDashed
                    "search_web" -> Lucide.Earth
                    "scrape_web" -> Lucide.Earth
                    else -> Lucide.Wrench
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Column {
                Text(
                    text = when (toolCall.toolName) {
                        "create_memory" -> stringResource(Res.string.chat_message_tool_create_memory)
                        "edit_memory" -> stringResource(Res.string.chat_message_tool_edit_memory)
                        "delete_memory" -> stringResource(Res.string.chat_message_tool_delete_memory)
                        "search_web" -> {
                            val query = runCatching {
                                JsonInstant.parseToJsonElement(toolCall.arguments).jsonObject["query"]?.jsonPrimitiveOrNull?.contentOrNull
                                    ?: ""
                            }.getOrDefault("")
                            stringResource(Res.string.chat_message_tool_search_web, query)
                        }
                        "scrape_web" -> stringResource(Res.string.chat_message_tool_scrape_web)
                        else -> stringResource(Res.string.chat_message_tool_call_generic, toolCall.toolName)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ExportedToolResult(toolResult: UIMessagePart.ToolResult) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            Icon(
                imageVector = when (toolResult.toolName) {
                    "create_memory", "edit_memory" -> Lucide.BookHeart
                    "delete_memory" -> Lucide.BookDashed
                    "search_web" -> Lucide.Earth
                    "scrape_web" -> Lucide.Earth
                    else -> Lucide.Wrench
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Column {
                Text(
                    text = when (toolResult.toolName) {
                        "create_memory" -> stringResource(Res.string.chat_message_tool_create_memory)
                        "edit_memory" -> stringResource(Res.string.chat_message_tool_edit_memory)
                        "delete_memory" -> stringResource(Res.string.chat_message_tool_delete_memory)
                        "search_web" -> {
                            val query =
                                toolResult.arguments.jsonObject["query"]?.jsonPrimitiveOrNull?.contentOrNull
                                    ?: ""
                            stringResource(Res.string.chat_message_tool_search_web, query)
                        }
                        "scrape_web" -> stringResource(Res.string.chat_message_tool_scrape_web)
                        else -> stringResource(Res.string.chat_message_tool_call_generic, toolResult.toolName)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

internal expect fun shareFile(context: PlatformContext, uri: Uri, mimeType: String)
