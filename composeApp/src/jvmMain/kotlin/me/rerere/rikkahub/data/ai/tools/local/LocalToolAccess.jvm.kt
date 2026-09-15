package me.rerere.rikkahub.data.ai.tools.local

import me.rerere.rikkahub.shared.currentPlatformKind
import me.rerere.rikkahub.shared.isMacOS

internal actual val calendarAccess: CalendarAccess by lazy {
    check(currentPlatformKind.isMacOS) { "Desktop calendar tools are only available on macOS." }
    MacCalendarAccess()
}

internal actual val screenTimeAccess: ScreenTimeAccess
    get() = error("Screen Time tools are unavailable on desktop.")
