package ru.fromchat.utils.haptic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ru.fromchat.ui.components.rememberHapticFeedbackInternal

@Composable
fun rememberHapticFeedback(): (HapticFeedbackEvent) -> Unit {
    val h = rememberHapticFeedbackInternal()
    return remember(h) { { event: HapticFeedbackEvent -> h(event.ordinal) } }
}
