package ru.fromchat.logging

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

actual object LogShare {
    actual fun shareText(title: String, text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        println("LogShare: copied text to clipboard ($title)")
    }

    actual fun shareFile(title: String, filePath: String, mimeType: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(File(filePath))
            } else {
                println("LogShare: file at $filePath ($title, $mimeType)")
            }
        }
    }
}
