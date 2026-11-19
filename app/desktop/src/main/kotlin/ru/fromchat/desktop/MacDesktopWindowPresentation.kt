package ru.fromchat.desktop

import javax.swing.SwingUtilities

internal object MacDesktopWindowPresentation {
    fun applySystemAnimationBehavior() {
        if (!isMacOs()) return
        MacNotificationCenter.applySystemWindowAnimationBehavior()
    }

    /** First cold open: set animation behavior and activate; do not re-makeKey (breaks Skiko text input). */
    fun onFirstComposeShow() {
        if (!isMacOs()) return
        SwingUtilities.invokeLater {
            MacNotificationCenter.applySystemWindowAnimationBehavior()
            MacNotificationCenter.activateApplication()
            repairMainWindowKeyboardFocus()
        }
    }

    /** Window was hidden (close-to-tray) and is visible again — use AppKit document presentation. */
    fun presentAfterHidden() {
        if (!isMacOs()) return
        SwingUtilities.invokeLater {
            MacNotificationCenter.applySystemWindowAnimationBehavior()
            MacNotificationCenter.presentMainWindows()
            repairMainWindowKeyboardFocus()
        }
    }

    /** Best-effort restore AWT/Skiko keyboard focus after AppKit presentation. */
    fun repairMainWindowKeyboardFocus() {
        if (!isMacOs()) return
        val window = MacDesktopWindowHost.mainWindow ?: return
        window.toFront()
        window.requestFocus()
        MacNotificationCenter.repairMainWindowKeyboardFocus()
        SwingUtilities.invokeLater {
            window.requestFocus()
            MacNotificationCenter.repairMainWindowKeyboardFocus()
        }
    }
}
