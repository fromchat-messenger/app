package ru.fromchat.legal

import com.pr0gramm3r101.utils.settings.settings
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.config.ServerConfig
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

object DocumentRepository {
    private const val RETRY_WINDOW_MS = 5_000L
    private const val RETRY_DELAY_MS = 1_000L

    private var cachedInstanceId: String? = null
    private val memoryCache = mutableMapOf<DocumentType, String>()

    fun invalidate() {
        memoryCache.clear()
        cachedInstanceId = null
    }

    private fun ensureInstanceFresh() {
        val active = CacheContext.activeInstanceId.value.trim()

        if (cachedInstanceId != null && cachedInstanceId != active) {
            memoryCache.clear()
        }

        cachedInstanceId = active
    }

    private fun persistentCacheKey(type: DocumentType) =
        "legal_doc_cache_v1_${CacheContext.activeInstanceId.value.trim().ifEmpty { "default" }}_${type.name}"

    private suspend fun readPersistentCache(type: DocumentType): String? {
        ensureInstanceFresh()

        return settings.getString(persistentCacheKey(type)).takeIf { it.isNotEmpty() }
    }

    private suspend fun writePersistentCache(type: DocumentType, markdown: String) {
        settings.putString(persistentCacheKey(type), markdown)
        memoryCache[type] = markdown
    }

    suspend fun fetch(type: DocumentType): DocumentLoadResult {
        ensureInstanceFresh()

        val start = Clock.System.now().toEpochMilliseconds()

        while (true) {
            runCatching {
                return DocumentLoadResult.Success(
                    ApiClient.http.get(
                        "${ServerConfig.apiBaseUrl}/static/${type.fileName}"
                    ).bodyAsText().also {
                        writePersistentCache(type, it)
                    },
                    isCached = false
                )
            }

            if (Clock.System.now().toEpochMilliseconds() - start >= RETRY_WINDOW_MS) {
                break
            }

            delay(RETRY_DELAY_MS.milliseconds)
        }

        (memoryCache[type] ?: readPersistentCache(type)).also {
            if (it != null) {
                memoryCache[type] = it
                return DocumentLoadResult.Success(it, isCached = true)
            }
        }

        return DocumentLoadResult.Failure
    }
}
