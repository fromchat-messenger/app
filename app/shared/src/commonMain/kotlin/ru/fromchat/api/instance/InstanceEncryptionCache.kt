package ru.fromchat.api.instance

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import ru.fromchat.config.ServerConfigData
import ru.fromchat.utils.settings.Settings as PlatformSettings

@Serializable
data class CachedInstanceEncryptionEntry(
    val instanceId: String,
    val configKey: String,
    val mode: String,
    val version: Int,
    val changedAt: String? = null,
    val protocol: String = "1",
)

private const val CACHE_KEY = "instance_encryption_cache_v1"
private val json = Json { ignoreUnknownKeys = true }

object InstanceEncryptionCache {
    private val settings = PlatformSettings()

    fun readAll(): List<CachedInstanceEncryptionEntry> {
        val raw = runBlocking {
            runCatching { settings.getString(CACHE_KEY) }.getOrDefault("")
        }.trim()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CachedInstanceEncryptionEntry.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun writeAll(entries: List<CachedInstanceEncryptionEntry>) {
        val encoded = json.encodeToString(ListSerializer(CachedInstanceEncryptionEntry.serializer()), entries)
        runBlocking { settings.putString(CACHE_KEY, encoded) }
    }

    fun find(instanceId: String, configKey: String): CachedInstanceEncryptionEntry? {
        val id = instanceId.trim()
        val key = configKey.trim()
        return readAll().firstOrNull { entry ->
            entry.instanceId == id || (key.isNotEmpty() && entry.configKey == key)
        }
    }

    fun upsert(snapshot: InstanceEncryptionSnapshot) {
        val entries = readAll().toMutableList()
        val idx = entries.indexOfFirst {
            it.instanceId == snapshot.instanceId || it.configKey == snapshot.configKey
        }
        val entry = CachedInstanceEncryptionEntry(
            instanceId = snapshot.instanceId,
            configKey = snapshot.configKey,
            mode = snapshot.mode.wireValue,
            version = snapshot.version,
            changedAt = snapshot.changedAt,
            protocol = snapshot.protocol,
        )
        if (idx >= 0) {
            entries[idx] = entry
        } else {
            entries.add(entry)
        }
        writeAll(entries)
    }

    fun snapshotFromEntry(entry: CachedInstanceEncryptionEntry): InstanceEncryptionSnapshot? {
        val mode = InstanceEncryptionMode.fromWire(entry.mode) ?: return null
        return InstanceEncryptionSnapshot(
            instanceId = entry.instanceId,
            configKey = entry.configKey,
            mode = mode,
            version = entry.version,
            changedAt = entry.changedAt,
            protocol = entry.protocol,
        )
    }

    fun snapshotForConfig(instanceId: String, config: ServerConfigData): InstanceEncryptionSnapshot? {
        val entry = find(instanceId, config.configKey()) ?: return null
        return snapshotFromEntry(entry)
    }
}
