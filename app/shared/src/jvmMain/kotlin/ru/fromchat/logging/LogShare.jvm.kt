package ru.fromchat.logging

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import ru.fromchat.utils.files.openLocalFileWithDefaultApp

actual object LogShare {
    actual fun shareText(title: String, text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        println("LogShare: copied text to clipboard ($title)")
    }

    actual fun shareFile(title: String, filePath: String, mimeType: String) {
        runCatching {
            openLocalFileWithDefaultApp(File(filePath))
        }.onFailure {
            println("LogShare: file at $filePath ($title, $mimeType)")
        }
    }
}
