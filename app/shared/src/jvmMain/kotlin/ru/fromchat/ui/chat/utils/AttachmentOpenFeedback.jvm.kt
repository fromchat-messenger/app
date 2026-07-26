package ru.fromchat.ui.chat.utils

internal actual fun showAttachmentOpenFailed(message: String) {
    System.err.println("Attachment open failed: $message")
}
