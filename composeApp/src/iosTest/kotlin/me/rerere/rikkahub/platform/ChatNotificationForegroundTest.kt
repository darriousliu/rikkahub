package me.rerere.rikkahub.platform

import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatNotificationForegroundTest {
    @Test
    fun observesNativeForegroundEventsAndUnregistersOnClose() {
        val values = mutableListOf<Boolean>()
        val stop = observeChatNotificationForeground { values += it }
        val center = NSNotificationCenter.defaultCenter
        try {
            assertEquals(1, values.size)
            center.postNotificationName(UIApplicationDidBecomeActiveNotification, null)
            center.postNotificationName(UIApplicationDidEnterBackgroundNotification, null)
            assertEquals(listOf(true, false), values.takeLast(2))
        } finally {
            stop()
        }
        val afterClose = values.toList()
        center.postNotificationName(UIApplicationDidBecomeActiveNotification, null)
        center.postNotificationName(UIApplicationDidEnterBackgroundNotification, null)
        assertEquals(afterClose, values)
    }
}
