package ru.fromchat.incognito

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Path-indexed files stored in an [IncognitoBlockVolume] (encrypted block container).
 * The container grows on write and compacts/shrinks after deletes like a VM disk.
 */
private const val DATA_REGION_START = 8192L

internal object IncognitoVolumeFileSystem {
    private const val CATALOG_LOGICAL_OFFSET = 0L
    private const val CATALOG_MAX_BYTES = 8192

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private var volume: IncognitoBlockVolume? = null
    private var catalog: VolumeCatalog = VolumeCatalog()

    fun attach(blockVolume: IncognitoBlockVolume) {
        synchronized(lock) {
            volume = blockVolume
            catalog = loadCatalog(blockVolume)
            if (catalog.nextDataOffset < DATA_REGION_START) {
                catalog = catalog.copy(nextDataOffset = DATA_REGION_START)
                saveCatalog(blockVolume, catalog)
            }
            syncVolumeSize(blockVolume)
        }
    }

    fun detach() {
        synchronized(lock) {
            volume?.let { vol ->
                compactAndShrink(vol)
                vol.fsync()
            }
            volume = null
            catalog = VolumeCatalog()
        }
    }

    fun flush() {
        synchronized(lock) {
            val vol = volume ?: return
            saveCatalog(vol, catalog)
            syncVolumeSize(vol)
            vol.fsync()
        }
    }

    fun relativeKeyForAppPath(absolutePath: String): String? {
        if (!IncognitoMode.active) return null
        val defaultRoot = defaultAppDataRootPath().replace('\\', '/').trimEnd('/')
        val normalizedPath = absolutePath.replace('\\', '/')
        return when {
            normalizedPath == defaultRoot -> ""
            normalizedPath.startsWith("$defaultRoot/") ->
                normalizedPath.removePrefix("$defaultRoot/")
            else -> null
        }
    }

    fun write(relativeKey: String, bytes: ByteArray) = synchronized(lock) {
        val vol = volume ?: return
        val existing = catalog.files[relativeKey]
        val offset = when {
            existing == null -> allocate(bytes.size.toLong())
            bytes.size <= existing.size -> existing.offset
            fitsInPlace(existing, bytes.size.toLong()) -> existing.offset
            else -> {
                catalog = catalog.copy(files = catalog.files - relativeKey)
                allocate(bytes.size.toLong())
            }
        }
        vol.write(offset, bytes)
        catalog = catalog.copy(
            files = catalog.files + (relativeKey to FileEntry(offset, bytes.size.toLong())),
        )
        saveCatalog(vol, catalog)
        syncVolumeSize(vol)
    }

    fun append(relativeKey: String, bytes: ByteArray) = synchronized(lock) {
        if (bytes.isEmpty()) return
        val existing = read(relativeKey)
        write(relativeKey, if (existing.isEmpty()) bytes else existing + bytes)
    }

    fun read(relativeKey: String): ByteArray = synchronized(lock) {
        val vol = volume ?: return ByteArray(0)
        val entry = catalog.files[relativeKey] ?: return ByteArray(0)
        if (entry.size <= 0) return ByteArray(0)
        vol.read(entry.offset, entry.size.toInt())
    }

    fun exists(relativeKey: String): Boolean = synchronized(lock) {
        catalog.files.containsKey(relativeKey)
    }

    fun delete(relativeKey: String) = synchronized(lock) {
        val vol = volume ?: return
        if (!catalog.files.containsKey(relativeKey)) return
        catalog = catalog.copy(files = catalog.files - relativeKey)
        saveCatalog(vol, catalog)
        compactAndShrink(vol)
    }

    fun fileSize(relativeKey: String): Long = synchronized(lock) {
        catalog.files[relativeKey]?.size ?: 0L
    }

    fun listFileNamesInDirectory(relativeDirKey: String): List<String> = synchronized(lock) {
        val prefix = relativeDirKey.trimEnd('/').let { if (it.isEmpty()) "" else "$it/" }
        catalog.files.keys
            .filter { key ->
                if (prefix.isEmpty()) !key.contains('/')
                else key.startsWith(prefix) && !key.removePrefix(prefix).contains('/')
            }
            .map { key -> key.removePrefix(prefix) }
            .distinct()
            .sorted()
    }

    fun deleteFilesWithPrefix(relativeDirKey: String, namePrefix: String) = synchronized(lock) {
        val vol = volume ?: return
        val prefix = relativeDirKey.trimEnd('/').let { if (it.isEmpty()) "" else "$it/" }
        val remove = catalog.files.keys.filter { key ->
            key.startsWith(prefix) && key.removePrefix(prefix).startsWith(namePrefix)
        }
        if (remove.isEmpty()) return
        catalog = catalog.copy(files = catalog.files.filterKeys { it !in remove.toSet() })
        saveCatalog(vol, catalog)
        compactAndShrink(vol)
    }

    fun ensureDirectory(relativeDirKey: String) = Unit

    private fun allocate(size: Long): Long {
        val end = catalog.nextDataOffset + size
        if (end > IncognitoContainerLimits.MAX_LOGICAL_BYTES) {
            throw IncognitoContainerCapacityException(
                "Incognito container exceeds ${IncognitoContainerLimits.MAX_LOGICAL_BYTES} byte limit",
            )
        }
        val offset = catalog.nextDataOffset
        catalog = catalog.copy(nextDataOffset = end)
        return offset
    }

    private fun fitsInPlace(existing: FileEntry, newSize: Long): Boolean {
        val end = existing.offset + newSize
        if (end <= catalog.nextDataOffset) return true
        return catalog.files.values.none { other ->
            other.offset != existing.offset &&
                rangesOverlap(existing.offset, newSize, other.offset, other.size)
        }
    }

    private fun rangesOverlap(aStart: Long, aSize: Long, bStart: Long, bSize: Long): Boolean {
        val aEnd = aStart + aSize
        val bEnd = bStart + bSize
        return aStart < bEnd && bStart < aEnd
    }

    /** Packs live files to the front of the data region and truncates trailing free space. */
    private fun compactAndShrink(vol: IncognitoBlockVolume) {
        if (catalog.files.isEmpty()) {
            catalog = catalog.copy(nextDataOffset = DATA_REGION_START)
            saveCatalog(vol, catalog)
            syncVolumeSize(vol)
            return
        }
        var cursor = DATA_REGION_START
        val ordered = catalog.files.entries.sortedBy { it.value.offset }
        val relocated = linkedMapOf<String, FileEntry>()
        for ((path, entry) in ordered) {
            val bytes = vol.read(entry.offset, entry.size.toInt())
            if (entry.offset != cursor) {
                vol.write(cursor, bytes)
            } else if (bytes.isNotEmpty()) {
                vol.write(cursor, bytes)
            }
            relocated[path] = FileEntry(cursor, entry.size)
            cursor += entry.size
        }
        catalog = catalog.copy(files = relocated, nextDataOffset = cursor)
        saveCatalog(vol, catalog)
        syncVolumeSize(vol)
    }

    private fun syncVolumeSize(vol: IncognitoBlockVolume) {
        val catalogEnd = CATALOG_MAX_BYTES.toLong()
        val dataEnd = maxOf(
            catalog.nextDataOffset,
            catalog.files.values.maxOfOrNull { it.offset + it.size } ?: DATA_REGION_START,
        )
        vol.truncateToLogicalByteCount(maxOf(catalogEnd, dataEnd))
    }

    private fun loadCatalog(vol: IncognitoBlockVolume): VolumeCatalog {
        val raw = vol.read(CATALOG_LOGICAL_OFFSET, CATALOG_MAX_BYTES)
        val text = raw.takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
        if (text.isBlank()) return VolumeCatalog(nextDataOffset = DATA_REGION_START)
        return runCatching { json.decodeFromString<VolumeCatalog>(text) }
            .getOrDefault(VolumeCatalog(nextDataOffset = DATA_REGION_START))
    }

    private fun saveCatalog(vol: IncognitoBlockVolume, catalog: VolumeCatalog) {
        val bytes = json.encodeToString(catalog).encodeToByteArray()
        require(bytes.size <= CATALOG_MAX_BYTES) { "Incognito catalog too large" }
        val padded = ByteArray(CATALOG_MAX_BYTES)
        bytes.copyInto(padded)
        vol.write(CATALOG_LOGICAL_OFFSET, padded)
    }
}

@Serializable
private data class VolumeCatalog(
    val nextDataOffset: Long = DATA_REGION_START,
    val files: Map<String, FileEntry> = emptyMap(),
)

@Serializable
private data class FileEntry(
    val offset: Long,
    val size: Long,
)
