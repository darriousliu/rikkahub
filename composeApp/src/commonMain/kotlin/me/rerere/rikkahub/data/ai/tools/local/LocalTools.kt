package me.rerere.rikkahub.data.ai.tools.local

import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.tts.provider.TTSManager

/**
 * Local tools that need no platform API beyond the shared abstractions.
 *
 * Tools that only some platforms can offer — Android's calendar and screen time — arrive through
 * [platformTools] instead of being referenced here.
 */
class LocalTools(
    private val eventBus: AppEventBus,
    private val settingsStore: SettingsStore,
    private val ttsManager: TTSManager?,
    private val platformTools: PlatformLocalTools = PlatformLocalTools.None,
) {
    val javascriptTool by lazy { buildJavascriptTool() }

    val timeTool by lazy { buildTimeInfoTool() }

    val clipboardTool by lazy { buildClipboardTool() }

    val ttsTool by lazy {
        ttsManager?.let { manager -> buildTextToSpeechTool(eventBus, manager, settingsStore) }
    }

    val askUserTool by lazy { buildAskUserTool() }

    fun getTools(options: List<LocalToolOption>): List<Tool> = buildList {
        if (options.contains(LocalToolOption.JavascriptEngine)) add(javascriptTool)
        if (options.contains(LocalToolOption.TimeInfo)) add(timeTool)
        if (options.contains(LocalToolOption.Clipboard)) add(clipboardTool)
        if (options.contains(LocalToolOption.Tts)) ttsTool?.let(::add)
        if (options.contains(LocalToolOption.AskUser)) add(askUserTool)
        addAll(platformTools.toolsFor(options))
    }
}

/** Supplies the local tools a specific platform can back. */
fun interface PlatformLocalTools {
    fun toolsFor(options: List<LocalToolOption>): List<Tool>

    companion object {
        val None: PlatformLocalTools = PlatformLocalTools { emptyList() }
    }
}
