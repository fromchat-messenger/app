package ru.fromchat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import ru.fromchat.utils.haptic.HapticFeedbackEvent

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no system back button; back is handled by navigation.
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberHapticFeedbackInternal(): (Int) -> Unit {
    return remember {
        { ordinal ->
            val style: UIImpactFeedbackStyle = when (ordinal) {
                HapticFeedbackEvent.MessageSent.ordinal -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
                HapticFeedbackEvent.ContextMenuOpened.ordinal -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
                HapticFeedbackEvent.SelectionModeEntered.ordinal -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
                else -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            }
            val generator = UIImpactFeedbackGenerator(style)
            generator.impactOccurred()
        }
    }
}
