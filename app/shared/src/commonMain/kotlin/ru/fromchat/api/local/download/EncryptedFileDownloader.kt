package ru.fromchat.api.local.download

import com.pr0gramm3r101.utils.files.PlatformFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.ensureActive
import ru.fromchat.api.local.cache.FileWriteSink
import kotlin.coroutines.coroutineContext

private const val DOWNLOAD_CHUNK_BYTES = 256 * 1024

/** Platform HTTP client for large encrypted downloads (must stream without buffering the full body). */
internal expect fun encryptedDownloadHttpClient(): HttpClient

private val encryptedDownloadHttp: HttpClient by lazy { encryptedDownloadHttpClient() }

/**
 * Streams an encrypted attachment HTTP response to [outputPath] without buffering the full body in RAM.
 */
internal suspend fun streamEncryptedFileToDisk(
    url: String,
    outputPath: String,
    rangeOffset: Long,
    bearerToken: String?,
    userAgent: String?,
    onChunkReceived: (receivedBytes: Long, totalBytes: Long?) -> Unit,
): Long = encryptedDownloadHttp.prepareGet(url) {
    bearerToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    userAgent?.let { header(HttpHeaders.UserAgent, it) }
    if (rangeOffset > 0L) {
        header(HttpHeaders.Range, "bytes=$rangeOffset-")
    }
}.execute { response ->
    if (response.status.value == 404 || response.status.value == 410) {
        error("HTTP ${response.status.value} for encrypted file download")
    }
    if (response.status.value !in 200..299) {
        error("HTTP ${response.status.value} for encrypted file download")
    }
    if (response.status == HttpStatusCode.OK && rangeOffset > 0L) {
        PlatformFileSystem.delete(outputPath)
    }

    val totalBytes = responseTotalBytes(response, rangeOffset)
    val channel = response.bodyAsChannel()
    var received = if (response.status == HttpStatusCode.PartialContent) rangeOffset else 0L
    val appendToPartial = rangeOffset > 0L && response.status == HttpStatusCode.PartialContent
    streamChannelToFile(
        channel = channel,
        outputPath = outputPath,
        append = appendToPartial,
    ) { chunkSize ->
        received += chunkSize
        onChunkReceived(received, totalBytes)
    }
    received
}

private suspend fun streamChannelToFile(
    channel: ByteReadChannel,
    outputPath: String,
    append: Boolean,
    onChunk: (Int) -> Unit,
) {
    val buffer = ByteArray(DOWNLOAD_CHUNK_BYTES)
    FileWriteSink(path = outputPath, append = append).use { sink ->
        while (!channel.isClosedForRead) {
            coroutineContext.ensureActive()
            val read = channel.readAvailable(buffer, offset = 0, length = buffer.size)
            when {
                read > 0 -> {
                    sink.write(buffer, offset = 0, length = read)
                    onChunk(read)
                }
                read < 0 -> break
                else -> if (!channel.awaitContent()) break
            }
        }
        sink.flush()
    }
}

private fun responseTotalBytes(response: HttpResponse, rangeOffset: Long): Long? {
    val contentRange = response.headers[HttpHeaders.ContentRange]
    if (contentRange != null) {
        val total = contentRange.substringAfterLast('/').toLongOrNull()
        if (total != null && total > 0L) return total
    }
    val contentLength: Long? = response.contentLength()
    return when {
        response.status == HttpStatusCode.PartialContent && contentLength != null ->
            rangeOffset + contentLength
        contentLength != null && contentLength > 0L -> contentLength
        else -> null
    }
}
