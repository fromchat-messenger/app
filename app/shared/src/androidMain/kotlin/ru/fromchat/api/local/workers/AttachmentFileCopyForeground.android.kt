package ru.fromchat.api.local.workers

import ru.fromchat.utils.UtilsLibrary

actual object AttachmentFileCopyForeground {
    actual fun onCopyStarted(storageKey: String, displayLabel: String?) {
        AttachmentFileCopyForegroundService.onJobStarted(
            UtilsLibrary.context.applicationContext,
            storageKey,
            displayLabel,
        )
    }

    actual fun onCopyFinished(storageKey: String) {
        AttachmentFileCopyForegroundService.onJobFinished(
            UtilsLibrary.context.applicationContext,
            storageKey,
        )
    }
}
