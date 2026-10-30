package ru.fromchat.api.local.download

import ru.fromchat.utils.UtilsLibrary
import ru.fromchat.api.local.workers.AttachmentDownloadForegroundService

actual object AttachmentDownloadForeground {
    actual fun onFileDownloadStarted(storageKey: String) {
        AttachmentDownloadForegroundService.onJobStarted(
            UtilsLibrary.context.applicationContext,
            storageKey,
        )
    }

    actual fun onFileDownloadProgress(percent: Int, displayLabel: String?) {
        AttachmentDownloadForegroundService.updateProgress(
            UtilsLibrary.context.applicationContext,
            percent,
            displayLabel,
        )
    }

    actual fun onFileDownloadFinished(storageKey: String) {
        AttachmentDownloadForegroundService.onJobFinished(
            UtilsLibrary.context.applicationContext,
            storageKey,
        )
    }
}
