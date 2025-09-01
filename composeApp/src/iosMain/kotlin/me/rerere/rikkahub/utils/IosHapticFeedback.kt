package me.rerere.rikkahub.utils

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import co.touchlab.kermit.Logger
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

object IosHapticFeedback : HapticFeedback {
    private val mediumImpactGenerator = UIImpactFeedbackGenerator()
    private val lightImpactGenerator =
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val selectionGenerator = UISelectionFeedbackGenerator()
    private val notificationGenerator = UINotificationFeedbackGenerator()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        Logger.i("IosHapticFeedback") { hapticFeedbackType.toString() }
        when (hapticFeedbackType) {
            HapticFeedbackType.Confirm -> notificationGenerator
                .notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)

            HapticFeedbackType.Reject -> notificationGenerator
                .notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)

            HapticFeedbackType.ContextClick,
            HapticFeedbackType.LongPress -> mediumImpactGenerator.impactOccurred()

            HapticFeedbackType.GestureEnd,
            HapticFeedbackType.GestureThresholdActivate,
            HapticFeedbackType.ToggleOff,
            HapticFeedbackType.ToggleOn,
            HapticFeedbackType.VirtualKey,
            HapticFeedbackType.KeyboardTap -> lightImpactGenerator.impactOccurred()

            HapticFeedbackType.SegmentFrequentTick,
            HapticFeedbackType.SegmentTick,
            HapticFeedbackType.TextHandleMove -> selectionGenerator.selectionChanged()
        }
    }
}
