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
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.jetbrains.compose.resources.StringResource
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

    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        if (enabled && !permissionGate(option)) return
        val newLocalTools = if (enabled) {
            assistant.localTools + option
        } else {
            assistant.localTools - option
        }
        onUpdate(assistant.copy(localTools = newLocalTools))
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
            localToolItem(
                option = LocalToolOption.JavascriptEngine,
                assistant = assistant,
                title = Res.string.assistant_page_local_tools_javascript_engine_title,
                description = Res.string.assistant_page_local_tools_javascript_engine_desc,
                onToggle = ::toggleLocalTool,
            )
            localToolItem(
                option = LocalToolOption.TimeInfo,
                assistant = assistant,
                title = Res.string.assistant_page_local_tools_time_info_title,
                description = Res.string.assistant_page_local_tools_time_info_desc,
                onToggle = ::toggleLocalTool,
            )
            localToolItem(
                option = LocalToolOption.Clipboard,
                assistant = assistant,
                title = Res.string.assistant_page_local_tools_clipboard_title,
                description = Res.string.assistant_page_local_tools_clipboard_desc,
                onToggle = ::toggleLocalTool,
            )
            localToolItem(
                option = LocalToolOption.Tts,
                assistant = assistant,
                title = Res.string.assistant_page_local_tools_tts_title,
                description = Res.string.assistant_page_local_tools_tts_desc,
                onToggle = ::toggleLocalTool,
            )
            localToolItem(
                option = LocalToolOption.AskUser,
                assistant = assistant,
                title = Res.string.assistant_page_local_tools_ask_user_title,
                description = Res.string.assistant_page_local_tools_ask_user_desc,
                onToggle = ::toggleLocalTool,
            )
            localToolItem(
                option = LocalToolOption.ScreenTime,
                assistant = assistant,
                title = Res.string.assistant_page_local_tools_screen_time_title,
                description = Res.string.assistant_page_local_tools_screen_time_desc,
                onToggle = ::toggleLocalTool,
            )
            localToolItem(
                option = LocalToolOption.Calendar,
                assistant = assistant,
                title = Res.string.assistant_page_local_tools_calendar_title,
                description = Res.string.assistant_page_local_tools_calendar_desc,
                onToggle = ::toggleLocalTool,
            )
        }
    }
}

private fun CardGroupScope.localToolItem(
    option: LocalToolOption,
    assistant: Assistant,
    title: StringResource,
    description: StringResource,
    onToggle: (LocalToolOption, Boolean) -> Unit,
) {
    if (option !in platformLocalToolOptions) return
    item(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        trailingContent = {
            Switch(
                checked = option in assistant.localTools,
                onCheckedChange = { onToggle(option, it) },
            )
        },
    )
}
