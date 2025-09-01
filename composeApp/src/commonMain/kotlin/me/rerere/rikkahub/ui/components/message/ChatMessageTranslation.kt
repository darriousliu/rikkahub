package me.rerere.rikkahub.ui.components.message

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.utils.*
import org.jetbrains.compose.resources.stringResource
import rikkahub.composeapp.generated.resources.*

@Composable
fun LanguageSelectionDialog(
    onLanguageSelected: (Locale) -> Unit,
    onClearTranslation: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    // 支持的语言列表
    val languages = remember {
        listOf(
            Locale.SIMPLIFIED_CHINESE,
            Locale.ENGLISH,
            Locale.TRADITIONAL_CHINESE,
            Locale.JAPANESE,
            Locale.KOREAN,
            Locale.FRENCH,
            Locale.GERMAN,
            Locale.ITALIAN,
        )
    }

    // 语言名称映射函数，原有的 locale.displayName 方法无法获取 emoji
    @Composable
    fun getLanguageDisplayName(locale: Locale): String {
        return when (locale) {
            Locale.SIMPLIFIED_CHINESE -> stringResource(Res.string.language_simplified_chinese)
            Locale.ENGLISH -> stringResource(Res.string.language_english)
            Locale.TRADITIONAL_CHINESE -> stringResource(Res.string.language_traditional_chinese)
            Locale.JAPANESE -> stringResource(Res.string.language_japanese)
            Locale.KOREAN -> stringResource(Res.string.language_korean)
            Locale.FRENCH -> stringResource(Res.string.language_french)
            Locale.GERMAN -> stringResource(Res.string.language_german)
            Locale.ITALIAN -> stringResource(Res.string.language_italian)
            else -> locale.getDisplayLanguage(Locale.current)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 标题
            Text(
                text = stringResource(Res.string.translation_language_selection_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth()
            )

            // 语言列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(languages) { language ->
                    Card(
                        onClick = {
                            onLanguageSelected(language)
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Lucide.Languages,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = getLanguageDisplayName(language),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }

                item {
                    Card(
                        onClick = {
                            onClearTranslation()
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Lucide.X,
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(Res.string.translation_clear),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleTranslationText(
    content: String,
    onClickCitation: (String) -> Unit
) {
    if (content.isNotBlank()) {
        var isCollapsed by remember { mutableStateOf(false) }

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )

        // Translation title and collapse button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Lucide.Languages,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(Res.string.translation_text),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 折叠/展开按钮
            IconButton(
                onClick = { isCollapsed = !isCollapsed },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isCollapsed) Lucide.ChevronDown else Lucide.ChevronUp,
                    contentDescription = if (isCollapsed) stringResource(Res.string.expand_translation) else stringResource(
                        Res.string.collapse_translation
                    ),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Translation content (collapsible)
        AnimatedVisibility(
            visible = !isCollapsed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                // Check if it's loading state
                val isTranslating = content == stringResource(Res.string.translating)

                if (isTranslating) {
                    // Show loading animation for translation
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val infiniteTransition = rememberInfiniteTransition(label = "loading")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )

                        Text(
                            text = stringResource(Res.string.translating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer(alpha = alpha)
                        )
                    }
                } else {
                    // Show normal translation content
                    MarkdownBlock(
                        content = content,
                        onClickCitation = onClickCitation,
                        modifier = Modifier
                            .padding(12.dp)
                            .animateContentSize()
                    )
                }
            }
        }
    }
}
