package ru.fromchat.ui.popup.jvm

import java.awt.AWTEvent
import java.awt.Color
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer

/** Clears AWT window chrome for transparent Compose popups (macOS context menus). */
internal fun applyTransparentComposeWindow(window: Window) {
    window.background = Color(0, 0, 0, 0)
    val frame = window as? JFrame ?: return
    frame.contentPane.background = Color(0, 0, 0, 0)
}

/** Dismiss after the opening click releases; ignore presses inside the popup itself. */
internal fun installOsPopupDismissOnClickOutside(
    popupWindow: Window,
    onDismiss: () -> Unit,
): () -> Unit {
    val toolkit = Toolkit.getDefaultToolkit()
    var armed = false
    val armTimer = Timer(250) { armed = true }.apply {
        isRepeats = false
        start()
    }
    val listener = AWTEventListener { event ->
        when (event.id) {
            MouseEvent.MOUSE_RELEASED -> armed = true
            MouseEvent.MOUSE_PRESSED -> {
                if (!armed) return@AWTEventListener
                val mouse = event as MouseEvent
                if (mouse.button != MouseEvent.BUTTON1) return@AWTEventListener
                if (mouse.isInsideWindow(popupWindow)) return@AWTEventListener
                SwingUtilities.invokeLater(onDismiss)
            }
        }
    }
    toolkit.addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
    return {
        armTimer.stop()
        toolkit.removeAWTEventListener(listener)
    }
}

/** Returns true when [event] landed inside [window]'s screen bounds. */
fun MouseEvent.isInsideWindow(window: Window): Boolean {
    val origin = window.locationOnScreen
    val bounds = Rectangle(origin.x, origin.y, window.width, window.height)
    return bounds.contains(locationOnScreen)
}
