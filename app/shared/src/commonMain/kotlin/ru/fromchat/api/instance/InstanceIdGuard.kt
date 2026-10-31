package ru.fromchat.api.instance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.fromchat.api.local.cache.CacheContext
import ru.fromchat.api.local.db.store.InstanceRegistryStore
import ru.fromchat.config.ServerConfigData
import kotlin.concurrent.Volatile

/**
 * Handles [INSTANCE_ID_HEADER] on API responses (main client + probe during server setup).
 */
object InstanceIdGuard {
    const val INSTANCE_ID_HEADER = "X-FromChat-Instance-Id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var probeConfig: ServerConfigData? = null

    fun onResponseHeader(headerValue: String?, config: ServerConfigData? = null) {
        val raw = headerValue?.trim().orEmpty()
        if (raw.isEmpty() || !isValidInstanceUuid(raw)) return
        val cfg = config ?: probeConfig ?: return
        scope.launch {
            val bound = InstanceRegistryStore.getActiveInstanceIdForConfig(cfg)?.trim().orEmpty()
            when {
                bound.isEmpty() -> InstanceRegistryStore.rebindServerInstance(cfg, raw)
                !bound.equals(raw, ignoreCase = true) ->
                    InstanceRegistryStore.rebindServerInstanceOnMismatch(cfg, bound, raw)
                else -> InstanceRegistryStore.registerInstanceEncountered(raw)
            }
            if (probeConfig == null && CacheContext.activeInstanceId.value.isNotEmpty()) {
                val userId = CacheContext.activeUserId.value
                if (!bound.equals(raw, ignoreCase = true)) {
                    CacheContext.setActiveInstance(raw, userId)
                }
            }
        }
    }
}
