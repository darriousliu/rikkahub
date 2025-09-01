package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.LightbulbOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkle
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import rikkahub.composeapp.generated.resources.*

@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    reasoningTokens: Int,
    onUpdateReasoningTokens: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        ReasoningPicker(
            reasoningTokens = reasoningTokens,
            onDismissRequest = { showPicker = false },
            onUpdateReasoningTokens = onUpdateReasoningTokens
        )
    }

    ToggleSurface(
        checked = ReasoningLevel.fromBudgetTokens(reasoningTokens).isEnabled,
        onClick = {
            showPicker = true
        },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.deepthink),
                    contentDescription = null,
                )
            }
            if (!onlyIcon) Text(stringResource(Res.string.setting_provider_page_reasoning))
        }
    }
}

@Composable
fun ReasoningPicker(
    reasoningTokens: Int,
    onDismissRequest: () -> Unit = {},
    onUpdateReasoningTokens: (Int) -> Unit,
) {
    val currentLevel = ReasoningLevel.fromBudgetTokens(reasoningTokens)
    ModalBottomSheet(
        onDismissRequest = {
            onDismissRequest()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReasoningLevelCard(
                selected = currentLevel == ReasoningLevel.OFF,
                icon = {
                    Icon(Lucide.LightbulbOff, null)
                },
                title = {
                    Text(stringResource(Res.string.reasoning_off))
                },
                description = {
                    Text(stringResource(Res.string.reasoning_off_desc))
                },
                onClick = {
                    onUpdateReasoningTokens(0)
                }
            )
            ReasoningLevelCard(
                selected = currentLevel == ReasoningLevel.AUTO,
                icon = {
                    Icon(Lucide.Sparkle, null)
                },
                title = {
                    Text(stringResource(Res.string.reasoning_auto))
                },
                description = {
                    Text(stringResource(Res.string.reasoning_auto_desc))
                },
                onClick = {
                    onUpdateReasoningTokens(-1)
                }
            )
            ReasoningLevelCard(
                selected = currentLevel == ReasoningLevel.LOW,
                icon = {
                    Icon(Lucide.Lightbulb, null)
                },
                title = {
                    Text(stringResource(Res.string.reasoning_light))
                },
                description = {
                    Text(stringResource(Res.string.reasoning_light_desc))
                },
                onClick = {
                    onUpdateReasoningTokens(1024)
                }
            )
            ReasoningLevelCard(
                selected = currentLevel == ReasoningLevel.MEDIUM,
                icon = {
                    Icon(Lucide.Lightbulb, null)
                },
                title = {
                    Text(stringResource(Res.string.reasoning_medium))
                },
                description = {
                    Text(stringResource(Res.string.reasoning_medium_desc))
                },
                onClick = {
                    onUpdateReasoningTokens(16_000)
                }
            )
            ReasoningLevelCard(
                selected = currentLevel == ReasoningLevel.HIGH,
                icon = {
                    Icon(Lucide.Lightbulb, null)
                },
                title = {
                    Text(stringResource(Res.string.reasoning_heavy))
                },
                description = {
                    Text(stringResource(Res.string.reasoning_heavy_desc))
                },
                onClick = {
                    onUpdateReasoningTokens(32_000)
                }
            )

            Card(
                modifier = Modifier.imePadding(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(stringResource(Res.string.reasoning_custom))
                    var input by remember(reasoningTokens) {
                        mutableStateOf(reasoningTokens.toString())
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { newValue ->
                            input = newValue
                            val newTokens = newValue.toIntOrNull()
                            if (newTokens != null) {
                                onUpdateReasoningTokens(newTokens)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasoningLevelCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: @Composable () -> Unit = {},
    title: @Composable () -> Unit = {},
    description: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val containerColor = animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    )
    val textColor = animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    )
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor.value,
            contentColor = textColor.value,
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                    title()
                }
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    description()
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun ReasoningPickerPreview() {
    MaterialTheme {
        var reasoningTokens by remember { mutableIntStateOf(0) }
        ReasoningPicker(
            onDismissRequest = {},
            reasoningTokens = reasoningTokens,
            onUpdateReasoningTokens = {
                reasoningTokens = it
            }
        )
    }
}
