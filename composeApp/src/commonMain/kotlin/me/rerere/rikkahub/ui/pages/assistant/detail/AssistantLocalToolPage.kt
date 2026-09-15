package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.generated.resources.Res
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_ask_user_desc
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_ask_user_title
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_calendar_desc
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_calendar_title
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_clipboard_desc
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_clipboard_title
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_javascript_engine_desc
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_javascript_engine_title
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_screen_time_desc
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_screen_time_permission_required
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_screen_time_title
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_time_info_desc
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_time_info_title
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_tts_desc
import me.rerere.rikkahub.generated.resources.assistant_page_local_tools_tts_title
import me.rerere.rikkahub.generated.resources.assistant_page_tab_local_tools
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.jetbrains.compose.resources.stringResource

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(stringResource(Res.string.assistant_page_tab_local_tools))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantLocalToolContent(
            innerPadding = innerPadding,
            assistant = assistant,
            onUpdate = { vm.update(it) },
        )
    }
}

@Composable
private fun AssistantLocalToolContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
) {
    val toaster = LocalToaster.current
    val permissionRequiredText =
        stringResource(Res.string.assistant_page_local_tools_screen_time_permission_required)
    val permissionGate = rememberLocalToolPermissionGate(
        onScreenTimePermissionRequired = {
            toaster.show(message = permissionRequiredText, type = ToastType.Warning)
        },
    )

    val scope = rememberCoroutineScope()
    val currentAssistant by rememberUpdatedState(assistant)
    val currentOnUpdate by rememberUpdatedState(onUpdate)
    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) = scope.launch {
        try {
            if (enabled && !permissionGate(option)) return@launch
            val newLocalTools = if (enabled) {
                currentAssistant.localTools + option
            } else {
                currentAssistant.localTools - option
            }
            currentOnUpdate(currentAssistant.copy(localTools = newLocalTools))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toaster.show(message = e.message ?: "Permission request failed", type = ToastType.Warning)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardGroup {
            if (LocalToolOption.JavascriptEngine in platformLocalToolOptions) {
                item(
                    headlineContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_javascript_engine_title))
                    },
                    supportingContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_javascript_engine_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
                            onCheckedChange = { toggleLocalTool(LocalToolOption.JavascriptEngine, it) }
                        )
                    }
                )
            }
            if (LocalToolOption.TimeInfo in platformLocalToolOptions) {
                item(
                    headlineContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_time_info_title))
                    },
                    supportingContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_time_info_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = assistant.localTools.contains(LocalToolOption.TimeInfo),
                            onCheckedChange = { toggleLocalTool(LocalToolOption.TimeInfo, it) }
                        )
                    }
                )
            }
            if (LocalToolOption.Clipboard in platformLocalToolOptions) {
                item(
                    headlineContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_clipboard_title))
                    },
                    supportingContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_clipboard_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = assistant.localTools.contains(LocalToolOption.Clipboard),
                            onCheckedChange = { toggleLocalTool(LocalToolOption.Clipboard, it) }
                        )
                    }
                )
            }
            if (LocalToolOption.Tts in platformLocalToolOptions) {
                item(
                    headlineContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_tts_title))
                    },
                    supportingContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_tts_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = assistant.localTools.contains(LocalToolOption.Tts),
                            onCheckedChange = { toggleLocalTool(LocalToolOption.Tts, it) }
                        )
                    }
                )
            }
            if (LocalToolOption.AskUser in platformLocalToolOptions) {
                item(
                    headlineContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_ask_user_title))
                    },
                    supportingContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_ask_user_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = assistant.localTools.contains(LocalToolOption.AskUser),
                            onCheckedChange = { toggleLocalTool(LocalToolOption.AskUser, it) }
                        )
                    }
                )
            }
            if (LocalToolOption.ScreenTime in platformLocalToolOptions) {
                item(
                    headlineContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_screen_time_title))
                    },
                    supportingContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_screen_time_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = assistant.localTools.contains(LocalToolOption.ScreenTime),
                            onCheckedChange = { toggleLocalTool(LocalToolOption.ScreenTime, it) }
                        )
                    }
                )
            }
            if (LocalToolOption.Calendar in platformLocalToolOptions) {
                item(
                    headlineContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_calendar_title))
                    },
                    supportingContent = {
                        Text(stringResource(Res.string.assistant_page_local_tools_calendar_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = assistant.localTools.contains(LocalToolOption.Calendar),
                            onCheckedChange = { toggleLocalTool(LocalToolOption.Calendar, it) }
                        )
                    }
                )
            }
        }
    }
}
