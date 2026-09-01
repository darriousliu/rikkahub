package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.event.AppEventBus

/** Screen time and calendar access exist only on Android. */
class AndroidLocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
) : PlatformLocalTools {
    private val screenTimeTool by lazy { buildScreenTimeTool(context, eventBus) }

    private val calendarQueryTool by lazy { buildCalendarQueryTool(context) }

    private val calendarCreateTool by lazy { buildCalendarCreateTool(context) }

    override fun toolsFor(options: List<LocalToolOption>): List<Tool> = buildList {
        if (options.contains(LocalToolOption.ScreenTime)) add(screenTimeTool)
        if (options.contains(LocalToolOption.Calendar)) {
            add(calendarQueryTool)
            add(calendarCreateTool)
        }
    }
}
