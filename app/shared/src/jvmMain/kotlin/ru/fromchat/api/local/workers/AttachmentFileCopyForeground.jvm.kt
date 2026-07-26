package ru.fromchat.api.local.workers

actual object AttachmentFileCopyForeground {
    actual fun onCopyStarted(storageKey: String, displayLabel: String?) = Unit
    actual fun onCopyFinished(storageKey: String) = Unit
}
