package ru.fromchat.incognito

import korlibs.crypto.SHA256
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.fromchat.logging.FromChatLogDirs

/**
 * Host filesystem storage with length-preserving AES-XTS payloads and an encrypted path map.
 * Logical app paths map to random host filenames; only ciphertext hits disk.
 */
internal object IncognitoVolumeFileSystem {
    private const val MAP_TWEAK_SCOPE = ".incognito-path-map"
    private val currentLogRelativeKey = "fromchat/logs/${FromChatLogDirs.CURRENT_LOG_FILE}"

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private var volumeKey: ByteArray? = null
    private var storageRoot: String? = null
    private var mapFilePath: String? = null
    private var pathMap: PathMap = PathMap()

    fun attach(volumeKey: ByteArray, absoluteStorageRoot: String) = synchronized(lock) {
        this.volumeKey = volumeKey.copyOf()
        storageRoot = absoluteStorageRoot.replace('\\', '/').trimEnd('/')
        IncognitoHostRawFs.attachRoot(storageRoot!!)
        mapFilePath = "$storageRoot/${mapHostBasename(volumeKey)}"
        pathMap = loadPathMapLocked(volumeKey, mapFilePath!!)
    }

    fun detach() = synchronized(lock) {
        persistPathMapLocked()
        IncognitoHostRawFs.detachRoot()
        volumeKey?.fill(0)
        volumeKey = null
        storageRoot = null
        mapFilePath = null
        pathMap = PathMap()
    }

    fun mapFileAbsolutePathOrNull(): String? = synchronized(lock) { mapFilePath }

    fun flush() = synchronized(lock) {
        persistPathMapLocked()
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
        val key = volumeKey ?: return
        ensureCapacityForAllocationLocked(bytes.size.toLong())
        val normalized = normalizeRelativeKey(relativeKey)
        val opaque = pathMap.entries[normalized] ?: newOpaqueHostName()
        writeEncryptedPayloadLocked(key, opaque, bytes)
        pathMap = pathMap.copy(entries = pathMap.entries + (normalized to opaque))
        persistPathMapLocked()
    }

    fun append(relativeKey: String, bytes: ByteArray) = synchronized(lock) {
        if (bytes.isEmpty()) return
        val prior = readLocked(relativeKey)
        writeLocked(relativeKey, if (prior.isEmpty()) bytes else prior + bytes)
    }

    fun read(relativeKey: String): ByteArray = synchronized(lock) {
        readLocked(relativeKey)
    }

    fun exists(relativeKey: String): Boolean = synchronized(lock) {
        pathMap.entries.containsKey(normalizeRelativeKey(relativeKey))
    }

    fun delete(relativeKey: String) = synchronized(lock) {
        deleteLocked(relativeKey)
    }

    fun fileSize(relativeKey: String): Long = synchronized(lock) {
        fileSizeLocked(relativeKey)
    }

    /** Host path to the opaque ciphertext file for [relativeKey] (creates map entry if needed). */
    fun resolveOrCreateOpaqueHostAbsolutePath(relativeKey: String): String = synchronized(lock) {
        val normalized = normalizeRelativeKey(relativeKey)
        val opaque = pathMap.entries[normalized] ?: newOpaqueHostName()
        if (normalized !in pathMap.entries) {
            pathMap = pathMap.copy(entries = pathMap.entries + (normalized to opaque))
            persistPathMapLocked()
        }
        payloadAbsolutePath(pathMap.entries.getValue(normalized))
    }

    fun listFileNamesInDirectory(relativeDirKey: String): List<String> = synchronized(lock) {
        val prefix = relativeDirKey.trimEnd('/').let { if (it.isEmpty()) "" else "$it/" }
        pathMap.entries.keys
            .filter { key ->
                if (prefix.isEmpty()) !key.contains('/')
                else key.startsWith(prefix) && !key.removePrefix(prefix).contains('/')
            }
            .map { key -> key.removePrefix(prefix) }
            .distinct()
            .sorted()
    }

    fun deleteFilesWithPrefix(relativeDirKey: String, namePrefix: String) = synchronized(lock) {
        val prefix = relativeDirKey.trimEnd('/').let { if (it.isEmpty()) "" else "$it/" }
        val remove = pathMap.entries.keys.filter { key ->
            key.startsWith(prefix) && key.removePrefix(prefix).startsWith(namePrefix)
        }
        if (remove.isEmpty()) return
        for (logical in remove) {
            deleteLocked(logical)
        }
    }

    fun ensureDirectory(relativeDirKey: String) = Unit

    private fun writeLocked(relativeKey: String, bytes: ByteArray) {
        val key = volumeKey ?: return
        ensureCapacityForAllocationLocked(bytes.size.toLong())
        val normalized = normalizeRelativeKey(relativeKey)
        val opaque = pathMap.entries[normalized] ?: newOpaqueHostName()
        writeEncryptedPayloadLocked(key, opaque, bytes)
        pathMap = pathMap.copy(entries = pathMap.entries + (normalized to opaque))
        persistPathMapLocked()
    }

    private fun readLocked(relativeKey: String): ByteArray {
        val key = volumeKey ?: return ByteArray(0)
        val normalized = normalizeRelativeKey(relativeKey)
        val opaque = pathMap.entries[normalized] ?: return ByteArray(0)
        val stored = IncognitoHostRawFs.readBytes(payloadAbsolutePath(opaque))
        if (stored.isEmpty()) return ByteArray(0)
        return IncognitoXtsCrypto.decrypt(key, opaque, stored)
    }

    private fun deleteLocked(relativeKey: String) {
        val normalized = normalizeRelativeKey(relativeKey)
        val opaque = pathMap.entries[normalized] ?: return
        IncognitoHostRawFs.delete(payloadAbsolutePath(opaque))
        pathMap = pathMap.copy(entries = pathMap.entries - normalized)
        persistPathMapLocked()
    }

    private fun fileSizeLocked(relativeKey: String): Long {
        val normalized = normalizeRelativeKey(relativeKey)
        val opaque = pathMap.entries[normalized] ?: return 0L
        return IncognitoHostRawFs.fileSize(payloadAbsolutePath(opaque))
    }

    private fun normalizeRelativeKey(relativeKey: String): String =
        relativeKey.replace('\\', '/').trimStart('/')

    private fun payloadAbsolutePath(opaqueHostName: String): String =
        "${storageRoot!!}/$opaqueHostName"

    private fun newOpaqueHostName(): String = IncognitoObfuscatedTempNames.randomTempFileName()

    private fun mapHostBasename(volumeKey: ByteArray): String {
        val digest = SHA256.digest(volumeKey + MAP_TWEAK_SCOPE.encodeToByteArray()).bytes
        return digest.copyOfRange(0, 12).joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    private fun writeEncryptedPayloadLocked(volumeKey: ByteArray, opaque: String, plaintext: ByteArray) {
        val ciphertext = IncognitoXtsCrypto.encrypt(volumeKey, opaque, plaintext)
        require(ciphertext.size == plaintext.size) {
            "Incognito XTS must preserve size (plain=${plaintext.size}, cipher=${ciphertext.size})"
        }
        val path = payloadAbsolutePath(opaque)
        IncognitoHostRawFs.writeBytes(path, ciphertext)
        IncognitoHostRawFs.fsync(path)
    }

    private fun loadPathMapLocked(volumeKey: ByteArray, mapPath: String): PathMap {
        if (!IncognitoHostRawFs.exists(mapPath)) return PathMap()
        val stored = IncognitoHostRawFs.readBytes(mapPath)
        if (stored.isEmpty()) return PathMap()
        val jsonBytes = IncognitoXtsCrypto.decrypt(volumeKey, MAP_TWEAK_SCOPE, stored)
        val text = jsonBytes.takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
        if (text.isBlank()) return PathMap()
        return runCatching { json.decodeFromString<PathMap>(text) }.getOrDefault(PathMap())
    }

    private fun persistPathMapLocked() {
        val key = volumeKey ?: return
        val mapPath = mapFilePath ?: return
        val jsonBytes = json.encodeToString(pathMap).encodeToByteArray()
        val padded = ByteArray(jsonBytes.size + 1)
        jsonBytes.copyInto(padded)
        val encrypted = IncognitoXtsCrypto.encrypt(key, MAP_TWEAK_SCOPE, padded)
        require(encrypted.size == padded.size)
        IncognitoHostRawFs.writeBytes(mapPath, encrypted)
        IncognitoHostRawFs.fsync(mapPath)
    }

    private fun liveBytesInUseLocked(): Long {
        val payloadBytes = pathMap.entries.values.sumOf { opaque ->
            IncognitoHostRawFs.fileSize(payloadAbsolutePath(opaque))
        }
        val mapBytes = mapFilePath?.let { IncognitoHostRawFs.fileSize(it) } ?: 0L
        return payloadBytes + mapBytes
    }

    private fun ensureCapacityForAllocationLocked(additionalBytes: Long) {
        val max = IncognitoContainerLimits.MAX_LOGICAL_BYTES
        val headroom = IncognitoContainerLimits.EVICTION_HEADROOM_BYTES
        if (liveBytesInUseLocked() + additionalBytes <= max - headroom) return
        batchEvictUntilLiveAtMostLocked((max - additionalBytes - headroom).coerceAtLeast(0L))
        if (liveBytesInUseLocked() + additionalBytes > max) {
            throw IncognitoContainerCapacityException(
                "Incognito storage exceeds $max byte limit",
            )
        }
    }

    private fun batchEvictUntilLiveAtMostLocked(targetLiveBytes: Long) {
        if (liveBytesInUseLocked() <= targetLiveBytes) return
        val evictableKeys = pathMap.entries
            .asSequence()
            .filter { (key, _) -> !IncognitoVolumeCacheEviction.isProtectedKey(key) }
            .sortedWith { a, b ->
                IncognitoVolumeCacheEviction.compareEvictionOrder(a.key, b.key, 0L, 0L)
            }
            .map { it.key }
            .toList()
        val remove = LinkedHashSet<String>()
        var live = liveBytesInUseLocked()
        for (logical in evictableKeys) {
            if (live <= targetLiveBytes) break
            if (logical == currentLogRelativeKey) continue
            remove += logical
            live -= fileSizeLocked(logical)
        }
        if (live > targetLiveBytes &&
            currentLogRelativeKey in pathMap.entries &&
            !IncognitoVolumeCacheEviction.isProtectedKey(currentLogRelativeKey)
        ) {
            remove += currentLogRelativeKey
        }
        for (logical in remove) {
            deleteLocked(logical)
        }
    }
}

@Serializable
private data class PathMap(
    val entries: Map<String, String> = emptyMap(),
)
