package ru.fromchat.utils.haptic

enum class HapticFeedbackEvent {
    ProfileOpened,
    ProfileClosed,
    MessageSent,
    ContextMenuOpened,
    SelectionModeEntered,
    /** Light tick on touch-down of a switch — fires before the state flips. */
    SwitchPress,
    /** Heavier click at the commit moment — fires exactly when the state flips. */
    SwitchToggle,
}