package ru.fromchat.desktop

/**
 * macOS close-to-background policy notes (implemented in [MainKt] / tray close handler).
 *
 * ## Window close → background (not quit)
 * The red traffic-light / window close hides the main window and keeps the JVM process
 * plus menu-bar tray alive (close-to-tray). Quit is only via tray Quit, File → Quit, or ⌘Q.
 * Dock icon stays (NSApplicationActivationPolicyRegular) so macOS can show the Dock
 * "Running in Background" indicator when no windows are visible.
 *
 * ## No Login Item / SMAppService
 * We intentionally do **not** register `SMAppService.mainAppService` (Open at Login /
 * Login Items & Extensions). Close-to-tray does not require that registration; the process
 * simply stays alive with a menu-bar tray while the window is hidden.
 */
internal object MacBackgroundLifecycle {
    const val LOG_TAG = "MacBackground"
}
