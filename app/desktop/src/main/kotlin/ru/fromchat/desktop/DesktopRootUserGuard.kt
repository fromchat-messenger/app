package ru.fromchat.desktop

import java.awt.GraphicsEnvironment
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/** Blocks startup when the process runs as root (broken paths, proxy trust, GNOME session data). */
internal fun abortIfRunningAsRoot() {
    val user = System.getProperty("user.name")?.trim().orEmpty()
    if (!user.equals("root", ignoreCase = true)) return

    val message =
        "FromChat was started as the root user.\n\n" +
            "Settings, logs, and caches would be stored under /root instead of your normal account. " +
            "That causes serious problems when you use the app normally " +
            "(wrong data directory, broken HiDPI, proxy certificates, and desktop integration).\n\n" +
            "Launch FromChat from your user account — application menu or " +
            "/opt/fromchat/bin/FromChat — never with sudo."

    if (GraphicsEnvironment.isHeadless()) {
        System.err.println("FromChat: $message")
        kotlin.system.exitProcess(1)
    }

    SwingUtilities.invokeAndWait {
        JOptionPane.showMessageDialog(
            null,
            message,
            "Do not run FromChat as root",
            JOptionPane.ERROR_MESSAGE,
        )
    }
    kotlin.system.exitProcess(1)
}