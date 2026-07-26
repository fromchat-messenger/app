package ru.fromchat.logging

import com.pr0gramm3r101.utils.files.PlatformFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Instant

enum class LogCleanMode {
    Size,
    All,
    Entries,
    Date,
}

data class LogCleanRequest(
    val mode: LogCleanMode,
    val maxTotalBytes: Long = 5L * 1024 * 1024,
    val keepNewestEntries: Int = 1_000,
    val deleteBefore: LocalDate? = null,
)

data class LogFileInfo(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val isGzip: Boolean,
)

enum class LogShareCompression {
    Uncompressed,
    Compressed,
}

object AppLogStore {
    private const val MAX_MEMORY_ENTRIES = 8_000
    private const val CONTINUATION_PREFIX = "\t"

    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val entries: StateFlow<List<AppLogEntry>> = _entries.asStateFlow()

    private var loadedFromDisk = false
    private var nextEntryId = 0L

    fun record(
        level: AppLogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val entry = AppLogEntry(
            id = nextEntryId++,
            timestamp = Clock.System.now(),
            level = level,
            tag = tag.trim().ifEmpty { "App" },
            message = message,
            stackTrace = throwable?.stackTraceToString(),
        )
        if (mutex.tryLock()) {
            try {
                appendEntry(entry)
            } finally {
                mutex.unlock()
            }
        } else {
            runBlocking { mutex.withLock { appendEntry(entry) } }
        }
    }

    private fun appendEntry(entry: AppLogEntry) {
        val lineBytes = (entry.formattedLine() + "\n").encodeToByteArray()
        PlatformFileSystem.appendBytes(FromChatLogDirs.currentLogPath(), lineBytes)
        val updated = (_entries.value + entry).let { list ->
            if (list.size <= MAX_MEMORY_ENTRIES) list else list.takeLast(MAX_MEMORY_ENTRIES)
        }
        _entries.value = updated
    }

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loadedFromDisk) return@withContext
            val text = runCatching { LogFileOps.readText(FromChatLogDirs.currentLogPath()) }
                .getOrDefault("")
            setEntriesFromParsed(parseLogText(text))
            loadedFromDisk = true
        }
    }

    suspend fun refreshFromDisk() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val text = runCatching { LogFileOps.readText(FromChatLogDirs.currentLogPath()) }
                .getOrDefault("")
            setEntriesFromParsed(parseLogText(text))
            loadedFromDisk = true
        }
    }

    suspend fun rotate() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentPath = FromChatLogDirs.currentLogPath()
            if (!PlatformFileSystem.exists(currentPath) || PlatformFileSystem.fileSize(currentPath) == 0L) {
                return@withLock
            }
            val stamp = Clock.System.now().toString().replace(':', '-')
            val archivePath = "${FromChatLogDirs.logsDirectoryPath()}/log-$stamp.log.gz"
            LogFileOps.gzipFile(currentPath, archivePath)
            PlatformFileSystem.delete(currentPath)
            _entries.value = emptyList()
            loadedFromDisk = true
        }
    }

    suspend fun clean(request: LogCleanRequest) = withContext(Dispatchers.IO) {
        mutex.withLock {
            when (request.mode) {
                LogCleanMode.All -> wipeAllLogs()
                LogCleanMode.Size -> cleanBySize(request.maxTotalBytes.coerceAtLeast(0L))
                LogCleanMode.Entries -> cleanByEntries(request.keepNewestEntries.coerceAtLeast(0))
                LogCleanMode.Date -> cleanByDate(request.deleteBefore)
            }
            refreshEntriesLocked()
        }
    }

    suspend fun exportAllText(): String = withContext(Dispatchers.IO) {
        mutex.withLock { buildExportTextLocked() }
    }

    suspend fun writeExportFile(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val exportPath = FromChatLogDirs.exportFilePath()
            PlatformFileSystem.writeBytes(exportPath, buildExportTextLocked().encodeToByteArray())
            exportPath
        }
    }

    fun listLogFiles(): List<LogFileInfo> {
        val dir = FromChatLogDirs.logsDirectoryPath()
        return PlatformFileSystem.listFileNamesInDirectory(dir)
            .filter { it != FromChatLogDirs.EXPORT_FILE && !it.startsWith("share-") }
            .sortedByDescending { it }
            .map { name ->
                val path = "$dir/$name"
                LogFileInfo(
                    name = name,
                    path = path,
                    sizeBytes = PlatformFileSystem.fileSize(path),
                    isGzip = name.endsWith(".gz"),
                )
            }
    }

    fun hasFilesBesidesCurrent(): Boolean =
        listLogFiles().any { it.name != FromChatLogDirs.CURRENT_LOG_FILE }

    suspend fun loadEntriesFromPath(
        path: String,
        onProgress: (Float) -> Unit = {},
    ): List<AppLogEntry> = withContext(Dispatchers.IO) {
        val text = if (path.endsWith(".gz")) {
            LogFileOps.readGzipText(path, onProgress)
        } else {
            onProgress(1f)
            LogFileOps.readText(path)
        }
        parseLogText(text)
    }

    suspend fun deleteLogFile(path: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            PlatformFileSystem.delete(path)
        }
    }

    suspend fun prepareMultiFileShareZip(paths: List<String>): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stamp = Clock.System.now().toString().replace(':', '-')
            val zipPath = FromChatLogDirs.tempShareFilePath("$stamp.zip")
            val entries = paths.map { path -> path.substringAfterLast('/') to path }
            LogFileOps.zipFiles(entries, zipPath)
            zipPath
        }
    }

    suspend fun prepareSharePath(
        sourcePath: String?,
        isCurrentLog: Boolean,
        compression: LogShareCompression,
        entries: List<AppLogEntry>? = null,
        onProgress: (Float) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stamp = Clock.System.now().toString().replace(':', '-')
            when {
                isCurrentLog -> {
                    val text = entries?.joinToString("\n") { it.formattedLine() }
                        ?: runCatching { LogFileOps.readText(FromChatLogDirs.currentLogPath()) }
                            .getOrDefault("")
                    when (compression) {
                        LogShareCompression.Uncompressed -> {
                            val path = FromChatLogDirs.tempShareFilePath("$stamp.log")
                            PlatformFileSystem.writeBytes(path, text.encodeToByteArray())
                            path
                        }
                        LogShareCompression.Compressed -> {
                            val plainPath = FromChatLogDirs.tempShareFilePath("$stamp.log")
                            PlatformFileSystem.writeBytes(plainPath, text.encodeToByteArray())
                            val gzipPath = "$plainPath.gz"
                            LogFileOps.gzipFile(plainPath, gzipPath)
                            gzipPath
                        }
                    }
                }

                compression == LogShareCompression.Compressed && sourcePath != null -> sourcePath

                sourcePath != null && sourcePath.endsWith(".gz") -> {
                    val path = FromChatLogDirs.tempShareFilePath("$stamp.log")
                    LogFileOps.gunzipToFile(sourcePath, path, onProgress)
                    path
                }

                sourcePath != null -> {
                    val path = FromChatLogDirs.tempShareFilePath("$stamp.log")
                    val bytes = LogFileOps.readText(sourcePath).encodeToByteArray()
                    PlatformFileSystem.writeBytes(path, bytes)
                    path
                }

                else -> FromChatLogDirs.exportFilePath()
            }
        }
    }

    private fun wipeAllLogs() {
        val dir = FromChatLogDirs.logsDirectoryPath()
        PlatformFileSystem.listFileNamesInDirectory(dir).forEach { name ->
            PlatformFileSystem.delete("$dir/$name")
        }
        _entries.value = emptyList()
    }

    private fun cleanBySize(maxTotalBytes: Long) {
        val dir = FromChatLogDirs.logsDirectoryPath()
        if (maxTotalBytes <= 0L) {
            wipeAllLogs()
            return
        }

        data class NamedSize(val path: String, val size: Long, val name: String)

        val files = PlatformFileSystem.listFileNamesInDirectory(dir)
            .map { name -> NamedSize("$dir/$name", PlatformFileSystem.fileSize("$dir/$name"), name) }
            .sortedBy { it.name }

        var total = files.sumOf { it.size }
        val current = files.firstOrNull { it.name == FromChatLogDirs.CURRENT_LOG_FILE }
        val archives = files.filter { it.name != FromChatLogDirs.CURRENT_LOG_FILE && it.name != FromChatLogDirs.EXPORT_FILE }

        for (archive in archives) {
            if (total <= maxTotalBytes) break
            PlatformFileSystem.delete(archive.path)
            total -= archive.size
        }

        current?.let { live ->
            if (total > maxTotalBytes && PlatformFileSystem.exists(live.path)) {
                val parsed = parseLogText(LogFileOps.readText(live.path))
                var kept = parsed
                while (kept.isNotEmpty() && total > maxTotalBytes) {
                    kept = kept.drop(1)
                    val rebuilt = kept.joinToString("\n") { it.formattedLine() }
                    PlatformFileSystem.writeBytes(live.path, rebuilt.encodeToByteArray())
                    total = archives.filter { PlatformFileSystem.exists(it.path) }.sumOf { file ->
                        PlatformFileSystem.fileSize(file.path)
                    } + PlatformFileSystem.fileSize(live.path)
                }
            }
        }
    }

    private fun cleanByEntries(keepNewestEntries: Int) {
        val currentPath = FromChatLogDirs.currentLogPath()
        if (!PlatformFileSystem.exists(currentPath)) return
        val kept = parseLogText(LogFileOps.readText(currentPath)).takeLast(keepNewestEntries)
        if (kept.isEmpty()) {
            PlatformFileSystem.delete(currentPath)
        } else {
            PlatformFileSystem.writeBytes(
                currentPath,
                kept.joinToString("\n") { it.formattedLine() }.encodeToByteArray(),
            )
        }
    }

    private fun cleanByDate(deleteBefore: LocalDate?) {
        if (deleteBefore == null) return
        val cutoffMs = deleteBefore.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

        val dir = FromChatLogDirs.logsDirectoryPath()
        PlatformFileSystem.listFileNamesInDirectory(dir)
            .filter { it.endsWith(".gz") }
            .forEach { name ->
                val archiveDate = archiveDateFromName(name)
                if (archiveDate != null && archiveDate < deleteBefore) {
                    PlatformFileSystem.delete("$dir/$name")
                }
            }

        val currentPath = FromChatLogDirs.currentLogPath()
        if (!PlatformFileSystem.exists(currentPath)) return
        val kept = parseLogText(LogFileOps.readText(currentPath)).filter {
            it.timestamp.toEpochMilliseconds() >= cutoffMs
        }
        if (kept.isEmpty()) {
            PlatformFileSystem.delete(currentPath)
        } else {
            PlatformFileSystem.writeBytes(
                currentPath,
                kept.joinToString("\n") { it.formattedLine() }.encodeToByteArray(),
            )
        }
    }

    private fun refreshEntriesLocked() {
        val text = runCatching { LogFileOps.readText(FromChatLogDirs.currentLogPath()) }.getOrDefault("")
        setEntriesFromParsed(parseLogText(text))
        loadedFromDisk = true
    }

    private fun setEntriesFromParsed(parsed: List<AppLogEntry>) {
        _entries.value = parsed.takeLast(MAX_MEMORY_ENTRIES)
        nextEntryId = (_entries.value.maxOfOrNull { it.id } ?: -1L) + 1L
    }

    private fun buildExportTextLocked(): String = buildString {
        val dir = FromChatLogDirs.logsDirectoryPath()
        PlatformFileSystem.listFileNamesInDirectory(dir)
            .filter { it.endsWith(".gz") }
            .sorted()
            .forEach { name ->
                appendLine("===== $name (gzip archive) =====")
            }
        appendLine("===== ${FromChatLogDirs.CURRENT_LOG_FILE} =====")
        append(runCatching { LogFileOps.readText(FromChatLogDirs.currentLogPath()) }.getOrDefault(""))
    }

    internal fun parseLogText(text: String): List<AppLogEntry> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<AppLogEntry>()
        var current: AppLogEntry? = null
        val traceLines = StringBuilder()
        var nextId = 0L

        fun flushTrace() {
            val entry = current ?: return
            if (traceLines.isNotEmpty()) {
                current = entry.copy(stackTrace = traceLines.toString().trimEnd())
                traceLines.clear()
            }
        }

        text.lineSequence().forEach { rawLine ->
            if (rawLine.startsWith(CONTINUATION_PREFIX) && current != null) {
                if (traceLines.isNotEmpty()) traceLines.append('\n')
                traceLines.append(rawLine.removePrefix(CONTINUATION_PREFIX))
                return@forEach
            }
            flushTrace()
            current?.let { result += it }
            current = parsePrimaryLine(rawLine, nextId++)
            traceLines.clear()
        }
        flushTrace()
        current?.let { result += it }
        return result
    }

    private fun parsePrimaryLine(line: String, id: Long): AppLogEntry? {
        if (line.isBlank()) return null

        parseFormattedPrimaryLine(line, id)?.let { return it }

        val parts = line.split('\t', limit = 4)
        if (parts.size < 4) return null
        val timestamp = runCatching { Instant.parse(parts[0]) }.getOrNull() ?: return null
        val level = AppLogLevel.fromLetter(parts[1].firstOrNull() ?: return null) ?: return null
        return AppLogEntry(
            id = id,
            timestamp = timestamp,
            level = level,
            tag = parts[2],
            message = parts[3],
        )
    }

    private fun archiveDateFromName(name: String): LocalDate? {
        val body = name.removePrefix("log-").removeSuffix(".log.gz")
        val datePart = body.takeWhile { it != 'T' && it != ' ' }
        return runCatching { LocalDate.parse(datePart) }.getOrNull()
    }
}
