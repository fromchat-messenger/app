@file:OptIn(ExperimentalForeignApi::class)

package ru.fromchat.ui.chat.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import ru.fromchat.utils.iosTopViewController

actual fun getFilenameFromUri(uri: String): String {
    val path = when {
        uri.startsWith("file://") -> NSURL.URLWithString(uri)?.path ?: uri
        else -> uri
    }
    return path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "file"
}

@Composable
actual fun rememberImagePicker(onResult: (List<String>) -> Unit): () -> Unit {
    var activeDelegate by remember { mutableStateOf<ImagePickerDelegate?>(null) }
    return remember(onResult) {
        {
            val host = iosTopViewController() ?: return@remember
            val picker = PHPickerViewController(
                configuration = PHPickerConfiguration().apply {
                    filter = PHPickerFilter.imagesFilter
                    selectionLimit = 0
                },
            )
            val delegate = ImagePickerDelegate { uris ->
                activeDelegate = null
                onResult(uris)
            }
            activeDelegate = delegate
            picker.delegate = delegate
            host.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@Composable
actual fun rememberFilePicker(onResult: (List<String>) -> Unit): () -> Unit {
    var activeDelegate by remember { mutableStateOf<FilePickerDelegate?>(null) }
    return remember(onResult) {
        {
            val host = iosTopViewController() ?: return@remember
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeItem),
                asCopy = true,
            ).apply {
                allowsMultipleSelection = true
            }
            val delegate = FilePickerDelegate { uris ->
                activeDelegate = null
                onResult(uris)
            }
            activeDelegate = delegate
            picker.delegate = delegate
            host.presentViewController(picker, animated = true, completion = null)
        }
    }
}

actual suspend fun getImageAspectRatio(uri: String): Float? {
    val (w, h) = getImageDimensions(uri) ?: return null
    if (h == 0) return null
    return w.toFloat() / h.toFloat()
}

actual suspend fun getImageDimensions(uri: String): Pair<Int, Int>? = withContext(Dispatchers.Default) {
    val path = when {
        uri.startsWith("file://") -> NSURL.URLWithString(uri)?.path
        else -> uri
    } ?: return@withContext null
    runCatching {
        val image = UIImage.imageWithContentsOfFile(path) ?: return@runCatching null
        pixelSize(image)
    }.getOrNull()
}

private fun pixelSize(image: UIImage): Pair<Int, Int>? {
    val w = (image.size.useContents { width } * image.scale).toInt()
    val h = (image.size.useContents { height } * image.scale).toInt()
    if (w <= 0 || h <= 0) return null
    return w to h
}

private fun pickerCacheDirectory(): String {
    val caches = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true,
    ).filterIsInstance<String>().firstOrNull().orEmpty()
    val dir = "$caches/fromchat/picker"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir
}

private fun copyUrlIntoPickerCache(url: NSURL): String? {
    val name = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: "file"
    val destPath = "${pickerCacheDirectory()}/${NSUUID().UUIDString}_$name"
    val destUrl = NSURL.fileURLWithPath(destPath)
    NSFileManager.defaultManager.removeItemAtPath(destPath, null)
    if (!NSFileManager.defaultManager.copyItemAtURL(url, toURL = destUrl, error = null)) {
        return null
    }
    return destUrl.absoluteString
}

private class ImagePickerDelegate(
    private val onFinished: (List<String>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    private var finished = false
    private val lock = NSLock()

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) {
            finish(emptyList())
            return
        }
        val collected = arrayOfNulls<String>(results.size)
        var remaining = results.size
        val typeId = UTTypeImage.identifier
        results.forEachIndexed { index, result ->
            val provider = result.itemProvider
            if (!provider.hasItemConformingToTypeIdentifier(typeId)) {
                val done = onItemLoaded {
                    remaining -= 1
                    remaining == 0
                }
                if (done) finish(collected.filterNotNull())
                return@forEachIndexed
            }
            provider.loadFileRepresentationForTypeIdentifier(typeId) { url, _ ->
                collected[index] = url?.let { copyUrlIntoPickerCache(it) }
                val done = onItemLoaded {
                    remaining -= 1
                    remaining == 0
                }
                if (done) {
                    dispatch_async(dispatch_get_main_queue()) {
                        finish(collected.filterNotNull())
                    }
                }
            }
        }
    }

    private inline fun onItemLoaded(block: () -> Boolean): Boolean {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    private fun finish(uris: List<String>) {
        if (finished) return
        finished = true
        onFinished(uris)
    }
}

private class FilePickerDelegate(
    private val onFinished: (List<String>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    private var finished = false

    private fun finish(uris: List<String>) {
        if (finished) return
        finished = true
        onFinished(uris)
    }

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val uris = didPickDocumentsAtURLs.mapNotNull { raw ->
            val url = raw as? NSURL ?: return@mapNotNull null
            copyUrlIntoPickerCache(url) ?: url.absoluteString
        }
        finish(uris)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(emptyList())
    }
}
