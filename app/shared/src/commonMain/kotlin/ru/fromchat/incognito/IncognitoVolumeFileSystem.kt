package ru.fromchat.incognito

import korlibs.crypto.SHA256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Host filesystem storage with length-preserving AES-XTS payloads and an encrypted path map.
 * Logical app paths map to random host filenames; only ciphertext hits disk.
 */
internal object IncognitoVolumeFileSystem {
    private const val MAP_TWEAK_SCOPE = ".incognito-path-map"
    private const val XTS_DATA_UNIT = 512

    /** Transient server ciphertext: stream on the host with normal file I/O (no XTS re-encrypt per chunk). */
    private val PLAINTEXT_HOST_RELATIVE_PREFIXES = listOf("encrypted_downloads/")

    private val json = Json { ignoreUnknownKeys = true }

    private var volumeKey: ByteArray? = null
    private var storageRoot: String? = null
    private var mapFilePath: String? = null
    private var pathMap: PathMap = PathMap()

    fun attach(volumeKey: ByteArray, absoluteStorageRoot: String) = withIncognitoVolumeLock {
        this.volumeKey = volumeKey.copyOf()
        storageRoot = absoluteStorageRoot.replace('\\', '/').trimEnd('/')
        IncognitoHostRawFs.attachRoot(storageRoot!!)
        mapFilePath = "$storageRoot/${mapHostBasename(volumeKey)}"
        pathMap = loadPathMapLocked(volumeKey, mapFilePath!!)
    }

    fun detach() = withIncognitoVolumeLock {
        persistPathMapLocked()
        IncognitoHostRawFs.detachRoot()
        volumeKey?.fill(0)
        volumeKey = null
        storageRoot = null
        mapFilePath = null
        pathMap = PathMap()
    }

    fun mapFileAbsolutePathOrNull(): String? = withIncognitoVolumeLock { mapFilePath }

    fun flush() = withIncognitoVolumeLock {
        persistPathMapLocked()
    }

    fun relativeKeyForAppPath(absolutePath: String): String? {
        if (!IncognitoMode.active) return null
        val defaultRoot = defaultAppDataRootPath().replace('\\', '/').trimEnd('/')
        val normalizedPath = absolutePath.replace('\\', '/')
        val relative = when {
            normalizedPath == defaultRoot -> ""
            normalizedPath.startsWith("$defaultRoot/") ->
                normalizedPath.removePrefix("$defaultRoot/")
            else -> return null
        }
        if (relative == "encrypted_downloads" ||
            PLAINTEXT_HOST_RELATIVE_PREFIXES.any { relative == it.trimEnd('/') || relative.startsWith(it) }
        ) {
            return null
        }
        return relative
    }

    fun write(relativeKey: String, bytes: ByteArray) = withIncognitoVolumeLock {
        val key = volumeKey ?: return@withIncognitoVolumeLock
        val normalized = normalizeRelativeKey(relativeKey)
        val opaque = pathMap.entries[normalized] ?: newOpaqueHostName()
        writeEncryptedPayloadLocked(key, opaque, bytes)
        pathMap = pathMap.copy(entries = pathMap.entries + (normalized to opaque))
        persistPathMapLocked()
    }

    fun append(relativeKey: String, bytes: ByteArray) = withIncognitoVolumeLock {
        if (bytes.isEmpty()) return@withIncognitoVolumeLock
        val key = volumeKey ?: return@withIncognitoVolumeLock
        val normalized = normalizeRelativeKey(relativeKey)
        val opaque = pathMap.entries[normalized]
        if (opaque == null) {
            writeLocked(relativeKey, bytes)
            return@withIncognitoVolumeLock
        }
        val hostPath = payloadAbsolutePath(opaque)
        val priorSize = IncognitoHostRawFs.fileSize(hostPath)
        if (priorSize == 0L) {
            writeEncryptedPayloadLocked(key, opaque, bytes)
            return@withIncognitoVolumeLock
        }
        val logicalOffset = priorSize.toInt()
        val misalign = logicalOffset % XTS_DATA_UNIT
        if (misalign == 0) {
            val cipher =
                IncognitoXtsCrypto.encryptAtLogicalOffset(key, opaque, bytes, logicalOffset)
            IncognitoHostRawFs.appendBytes(hostPath, cipher)
        } else {
            val unitStart = logicalOffset - misalign
            val tailCipher =
                IncognitoHostRawFs.readRange(hostPath, unitStart.toLong(), misalign)
            val tailPlain =
                IncognitoXtsCrypto.decryptAtLogicalOffset(key, opaque, tailCipher, unitStart)
            val merged = ByteArray(tailPlain.size + bytes.size)
            tailPlain.copyInto(merged)
            bytes.copyInto(merged, destinationOffset = tailPlain.size)
            val newCipher =
                IncognitoXtsCrypto.encryptAtLogicalOffset(key, opaque, merged, unitStart)
            IncognitoHostRawFs.writeBytesAt(hostPath, unitStart.toLong(), newCipher)
        }
    }

    fun read(relativeKey: String): ByteArray = withIncognitoVolumeLock {
        readLocked(relativeKey)
    }

    fun exists(relativeKey: String): Boolean = withIncognitoVolumeLock {
        existsLocked(relativeKey)
    }

    fun delete(relativeKey: String) = withIncognitoVolumeLock {
        deleteLocked(relativeKey)
    }

    fun fileSize(relativeKey: String): Long = withIncognitoVolumeLock {
        fileSizeLocked(relativeKey)
    }

    /** Host path to the opaque ciphertext file for [relativeKey] (creates map entry if needed). */
    fun resolveOrCreateOpaqueHostAbsolutePath(relativeKey: String): String = withIncognitoVolumeLock {
        val normalized = normalizeRelativeKey(relativeKey)
        val opaque = pathMap.entries[normalized] ?: newOpaqueHostName()
        if (normalized !in pathMap.entries) {
            pathMap = pathMap.copy(entries = pathMap.entries + (normalized to opaque))
            persistPathMapLocked()
        }
        payloadAbsolutePath(pathMap.entries.getValue(normalized))
    }

    fun listFileNamesInDirectory(relativeDirKey: String): List<String> = withIncognitoVolumeLock {
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

    fun deleteFilesWithPrefix(relativeDirKey: String, namePrefix: String) = withIncognitoVolumeLock {
        val prefix = relativeDirKey.trimEnd('/').let { if (it.isEmpty()) "" else "$it/" }
        val remove = pathMap.entries.keys.filter { key ->
            key.startsWith(prefix) && key.removePrefix(prefix).startsWith(namePrefix)
        }
        if (remove.isEmpty()) return@withIncognitoVolumeLock
        for (logical in remove) {
            deleteLocked(logical)
        }
    }

    fun ensureDirectory(relativeDirKey: String) = withIncognitoVolumeLock {
        val normalized = normalizeRelativeKey(relativeDirKey).trimEnd('/')
        if (normalized.isEmpty()) return@withIncognitoVolumeLock
        val marker = directoryMarkerKey(normalized)
        if (marker !in pathMap.entries) {
            writeLocked(marker, ByteArray(0))
        }
    }

    private fun writeLocked(relativeKey: String, bytes: ByteArray) {
        val key = volumeKey ?: return
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

    private fun directoryMarkerKey(normalizedDir: String): String = "$normalizedDir/.incognito-dir"

    private fun existsLocked(relativeKey: String): Boolean {
        val normalized = normalizeRelativeKey(relativeKey).trimEnd('/')
        if (pathMap.entries.containsKey(normalized)) return true
        if (normalized.isEmpty()) return pathMap.entries.isNotEmpty()
        if (pathMap.entries.containsKey(directoryMarkerKey(normalized))) return true
        val prefix = "$normalized/"
        return pathMap.entries.keys.any { it.startsWith(prefix) }
    }

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
}

@Serializable
private data class PathMap(
    val entries: Map<String, String> = emptyMap(),
)
