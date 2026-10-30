package ru.fromchat.ui.main

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import ru.fromchat.desktop.DesktopMenuCommand
import ru.fromchat.desktop.DesktopMenuCommands

/** Ctrl on Windows/Linux/Android; ⌘ on macOS / iPadOS with hardware keyboard. */
fun KeyEvent.usesAppMenuShortcutModifier(): Boolean = isCtrlPressed || isMetaPressed

/**
 * Shared menu shortcuts (aligned with desktop menu bar / Win-Linux window chrome).
 * Returns true when the event was handled.
 */
fun handleAppMenuShortcutKeyEvent(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (!event.usesAppMenuShortcutModifier()) return false
    return when {
        !event.isShiftPressed && event.key == Key.N -> {
            DesktopMenuCommands.emit(DesktopMenuCommand.NewChat)
            true
        }
        !event.isShiftPressed && event.key == Key.F -> {
            DesktopMenuCommands.emit(DesktopMenuCommand.SearchConversations)
            true
        }
        event.isShiftPressed && event.key == Key.S -> {
            DesktopMenuCommands.emit(DesktopMenuCommand.EnterChatListSelection)
            true
        }
        event.isShiftPressed && event.key == Key.A -> {
            DesktopMenuCommands.emit(DesktopMenuCommand.OpenAbout)
            true
        }
        else -> false
    }
}
