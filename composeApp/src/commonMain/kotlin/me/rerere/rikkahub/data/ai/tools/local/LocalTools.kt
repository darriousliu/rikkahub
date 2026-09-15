package me.rerere.rikkahub.data.ai.tools.local

import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.shared.PlatformKind
import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.shared.isLinux
import me.rerere.rikkahub.shared.isWindows
import me.rerere.tts.provider.TTSManager

class LocalTools(
    private val eventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val ttsManager: TTSManager?,
) {
    val javascriptTool by lazy { buildJavascriptTool() }

    val timeTool by lazy { buildTimeInfoTool() }

    val clipboardTool by lazy { buildClipboardTool() }

    val ttsTool by lazy {
        ttsManager?.let { manager -> buildTextToSpeechTool(eventBus, manager, settingsStore) }
    }

    val askUserTool by lazy { buildAskUserTool() }

    val screenTimeTool by lazy { buildScreenTimeTool() }

    val calendarQueryTool by lazy { buildCalendarQueryTool() }

    val calendarCreateTool by lazy { buildCalendarCreateTool() }

    fun getTools(options: List<LocalToolOption>): List<Tool> = buildList {
        if (options.contains(LocalToolOption.JavascriptEngine)) add(javascriptTool)
        if (options.contains(LocalToolOption.TimeInfo)) add(timeTool)
        if (options.contains(LocalToolOption.Clipboard)) add(clipboardTool)
        if (options.contains(LocalToolOption.Tts)) ttsTool?.let(::add)
        if (options.contains(LocalToolOption.AskUser)) add(askUserTool)
        if (LocalToolOption.ScreenTime in options && currentPlatformKind != PlatformKind.DESKTOP) add(screenTimeTool)
        if (LocalToolOption.Calendar in options && !currentPlatformKind.isLinux && !currentPlatformKind.isWindows) {
            add(calendarQueryTool)
            add(calendarCreateTool)
        }
    }
}
