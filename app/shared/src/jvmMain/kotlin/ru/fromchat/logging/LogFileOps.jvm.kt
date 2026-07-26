package ru.fromchat.logging

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual object LogFileOps {
    actual fun readText(path: String): String {
        val file = File(path)
        if (!file.isFile) return ""
        return runCatching { file.readText() }.getOrDefault("")
    }

    actual fun readBytes(path: String): ByteArray {
        val file = File(path)
        if (!file.isFile) return ByteArray(0)
        return runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
    }

    actual suspend fun gzipFile(sourcePath: String, destinationPath: String) = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        if (!source.isFile) return@withContext
        val dest = File(destinationPath)
        dest.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            GZIPOutputStream(dest.outputStream()).use { gzip ->
                input.copyTo(gzip)
            }
        }
        source.delete()
    }

    actual suspend fun readGzipText(path: String, onProgress: (Float) -> Unit): String =
        withContext(Dispatchers.IO) {
            gunzipToByteArray(path, onProgress).decodeToString()
        }

    actual suspend fun gunzipToFile(
        sourcePath: String,
        destinationPath: String,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val bytes = gunzipToByteArray(sourcePath, onProgress)
        val dest = File(destinationPath)
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
    }

    actual suspend fun zipFiles(
        entries: List<Pair<String, String>>,
        destinationPath: String,
    ) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        val dest = File(destinationPath)
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream()).use { zip ->
            entries.forEach { (entryName, sourcePath) ->
                val source = File(sourcePath)
                if (!source.isFile) return@forEach
                zip.putNextEntry(ZipEntry(entryName))
                FileInputStream(source).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun gunzipToByteArray(path: String, onProgress: (Float) -> Unit): ByteArray {
        val file = File(path)
        if (!file.isFile) {
            onProgress(1f)
            return ByteArray(0)
        }
        val total = file.length().coerceAtLeast(1L)
        val output = ByteArrayOutputStream()
        FileInputStream(file).use { input ->
            GZIPInputStream(input).use { gzip ->
                val buffer = ByteArray(8_192)
                var read: Int
                var consumed = 0L
                while (gzip.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    consumed = (consumed + read).coerceAtMost(total)
                    onProgress((consumed.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
        return output.toByteArray()
    }
}
