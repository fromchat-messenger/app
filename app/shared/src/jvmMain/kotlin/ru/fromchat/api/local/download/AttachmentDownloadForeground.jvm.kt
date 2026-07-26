package ru.fromchat.api.local.download

actual object AttachmentDownloadForeground {
    actual fun onFileDownloadStarted(storageKey: String) = Unit

    actual fun onFileDownloadProgress(percent: Int, displayLabel: String?) = Unit

    actual fun onFileDownloadFinished(storageKey: String) = Unit
}
