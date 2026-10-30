@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ru.fromchat.api.local.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSDownloadsDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import ru.fromchat.utils.iosTopViewController

private const val EXPORT_SUBDIR = "save_export"

internal fun sanitizeExportFilename(filename: String): String =
    filename.trim().replace('/', '_').replace('\\', '_').ifBlank { "image.jpg" }

private fun exportStagingDirectory(): String {
    val dir =
        "${NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true,
        ).filterIsInstance<String>().firstOrNull().orEmpty()}/$EXPORT_SUBDIR"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir
}

private fun writeExportStagingFile(filename: String, bytes: ByteArray): String? {
    val dir = exportStagingDirectory()
    val path = "$dir/${sanitizeExportFilename(filename)}"
    val nsData = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    return if (nsData.writeToFile(path, true)) path else null
}

/** Default folder in the Files app (Downloads when available, else Documents). */
private fun defaultExportDirectoryUrl(): NSURL? {
    val manager = NSFileManager.defaultManager
    manager.URLForDirectory(
        NSDownloadsDirectory,
        NSUserDomainMask,
        null,
        false,
        null,
    )?.let { return it }
    return manager.URLForDirectory(
        NSDocumentDirectory,
        NSUserDomainMask,
        null,
        false,
        null,
    )
}

private class ExportDocumentPickerDelegate(
    private val stagingPath: String,
    private val onFinished: (Boolean) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    private var finished = false

    private fun finish(success: Boolean) {
        if (finished) return
        finished = true
        NSFileManager.defaultManager.removeItemAtPath(stagingPath, null)
        onFinished(success)
    }

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        finish(didPickDocumentsAtURLs.isNotEmpty())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(false)
    }
}

@Composable
actual fun rememberPlatformSaveMessageImage(
    onComplete: (Boolean) -> Unit,
): (SavableMessageImage, ByteArray) -> Unit {
    var activeDelegate by remember { mutableStateOf<ExportDocumentPickerDelegate?>(null) }

    return remember(onComplete) {
        { savable: SavableMessageImage, bytes: ByteArray ->
            val stagingPath = writeExportStagingFile(savable.filename, bytes)
            if (stagingPath == null) {
                onComplete(false)
                return@remember
            }
            val host = iosTopViewController()
            if (host == null) {
                NSFileManager.defaultManager.removeItemAtPath(stagingPath, null)
                onComplete(false)
                return@remember
            }
            val delegate = ExportDocumentPickerDelegate(stagingPath) { success ->
                activeDelegate = null
                onComplete(success)
            }
            activeDelegate = delegate
            val picker = UIDocumentPickerViewController(
                forExportingURLs = listOf(NSURL.fileURLWithPath(stagingPath)),
                asCopy = true,
            )
            defaultExportDirectoryUrl()?.let { picker.directoryURL = it }
            picker.delegate = delegate
            host.presentViewController(picker, animated = true, completion = null)
        }
    }
}
