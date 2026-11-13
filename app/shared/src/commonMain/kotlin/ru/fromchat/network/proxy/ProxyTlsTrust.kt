package ru.fromchat.network.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.fromchat.api.local.AttachmentMediaLog
import ru.fromchat.utils.settings.Settings as PlatformSettings

@Serializable
data class TrustedTlsCertificate(
    val id: String,
    val host: String,
    val label: String,
    val pem: String,
    val addedAtEpochMs: Long = 0,
)

object ProxyTlsTrust {
    private const val STORE_KEY = "proxy_trusted_certs_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(TrustedTlsCertificate.serializer())
    private val settings = PlatformSettings()

    @Volatile
    var sessionHost: String? = null
        private set

    @Volatile
    var sessionPem: String? = null
        private set

    fun clearSession() {
        sessionHost = null
        sessionPem = null
    }

    fun trustThisSession(host: String, pem: String) {
        sessionHost = host.trim()
        sessionPem = pem.trim()
    }

    fun trustPersistently(host: String, label: String, pem: String) {
        runBlocking {
            trustPersistentlySuspend(host, label, pem)
        }
    }

    suspend fun trustPersistentlySuspend(host: String, label: String, pem: String) {
        withContext(Dispatchers.IO) {
            val trimmedHost = host.trim()
            val trimmedPem = pem.trim()
            val id = certId(trimmedHost, trimmedPem)
            val list = readPersisted().toMutableList()
            list.removeAll { it.id == id || (it.host == trimmedHost && it.pem == trimmedPem) }
            list.add(
                TrustedTlsCertificate(
                    id = id,
                    host = trimmedHost,
                    label = label.trim(),
                    pem = trimmedPem,
                    addedAtEpochMs = AttachmentMediaLog.nowMs(),
                ),
            )
            savePersisted(list)
        }
    }

    fun removePersisted(id: String) {
        runBlocking {
            withContext(Dispatchers.IO) {
                savePersisted(readPersisted().filterNot { it.id == id })
            }
        }
    }

    fun loadPersisted(): List<TrustedTlsCertificate> = runBlocking { readPersisted() }

    suspend fun readPersisted(): List<TrustedTlsCertificate> = withContext(Dispatchers.IO) {
        val raw = settings.getString(STORE_KEY, "")
        if (raw.isBlank()) return@withContext emptyList()
        runCatching {
            json.decodeFromString(listSerializer, raw)
        }.getOrDefault(emptyList())
    }

    fun extraPemForHost(host: String): List<String> {
        val h = host.trim()
        val persisted = loadPersisted().filter { it.host.equals(h, ignoreCase = true) }.map { it.pem }
        val session = if (sessionHost.equals(h, ignoreCase = true)) {
            sessionPem?.let { listOf(it) } ?: emptyList()
        } else {
            emptyList()
        }
        return (persisted + session).distinct()
    }

    fun hasTrustForPem(host: String, pem: String?): Boolean {
        if (pem.isNullOrBlank()) return false
        val normalized = pem.trim()
        return extraPemForHost(host).any { it.trim() == normalized }
    }

    private suspend fun savePersisted(list: List<TrustedTlsCertificate>) {
        settings.putString(STORE_KEY, json.encodeToString(listSerializer, list))
    }

    private fun certId(host: String, pem: String): String =
        "${host.lowercase()}_${pem.hashCode().toUInt().toString(16)}"
}

fun tlsHostFromUrl(url: String): String =
    url.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore(':')
