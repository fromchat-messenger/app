package ru.fromchat.api.local.download

import java.awt.Desktop
import java.io.File
import java.net.URI

actual suspend fun openCachedAttachmentFile(
    cacheUri: String,
    mimeType: String,
    displayFilename: String?,
): Boolean {
    return runCatching {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.OPEN)) return false
        when {
            cacheUri.startsWith("file:") -> desktop.open(File(URI(cacheUri)))
            cacheUri.startsWith("http://") || cacheUri.startsWith("https://") ->
                desktop.browse(URI(cacheUri))
            else -> desktop.open(File(cacheUri))
        }
        true
    }.getOrDefault(false)
}
