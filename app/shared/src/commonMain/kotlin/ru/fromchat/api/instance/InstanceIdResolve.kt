package ru.fromchat.api.instance

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.InstanceRegistryStore
import ru.fromchat.config.ServerConfigData

private val UUID_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

fun isValidInstanceUuid(value: String): Boolean = UUID_REGEX.matches(value.trim())

sealed interface InstanceIdResolveResult {
    data class Cached(val instanceId: String) : InstanceIdResolveResult
    data class Fetched(val instanceId: String) : InstanceIdResolveResult
    data class InstanceIdChanged(val oldId: String, val newId: String) : InstanceIdResolveResult
    data object Unsupported : InstanceIdResolveResult
    data object Timeout : InstanceIdResolveResult
    data object Unreachable : InstanceIdResolveResult
}

private const val INSTANCE_FETCH_MS = 10_000L

/**
 * Resolves instance id for [config]: reads DB binding, optionally fetches `/instance_id`.
 * On mismatch with cached binding, rebinds via [InstanceRegistryStore] (no partition delete).
 */
suspend fun resolveInstanceId(
    config: ServerConfigData,
    apiBaseUrl: String,
    forceNetwork: Boolean,
    allowCachedOnFailure: Boolean = true,
): InstanceIdResolveResult {
    val cached = InstanceRegistryStore.getActiveInstanceIdForConfig(config)?.trim().orEmpty()
    if (!forceNetwork && cached.isNotEmpty() && isValidInstanceUuid(cached)) {
        return InstanceIdResolveResult.Cached(cached)
    }

    val fetchResult = runCatching {
        withTimeout(INSTANCE_FETCH_MS) {
            ApiClient.fetchServerInstanceId(apiBaseUrl)
        }
    }
    if (fetchResult.isFailure) {
        return networkFailureToResolveResult(
            e = fetchResult.exceptionOrNull(),
            cached = cached,
            allowCachedOnFailure = allowCachedOnFailure,
        )
    }
    val fetched = fetchResult.getOrThrow().trim()

    if (fetched.isEmpty() || !isValidInstanceUuid(fetched)) {
        return InstanceIdResolveResult.Unsupported
    }

    if (cached.isEmpty()) {
        InstanceRegistryStore.rebindServerInstance(config, fetched)
        return InstanceIdResolveResult.Fetched(fetched)
    }
    if (cached.equals(fetched, ignoreCase = true)) {
        InstanceRegistryStore.registerInstanceEncountered(fetched)
        return InstanceIdResolveResult.Fetched(fetched)
    }
    InstanceRegistryStore.rebindServerInstanceOnMismatch(config, cached, fetched)
    return InstanceIdResolveResult.InstanceIdChanged(cached, fetched)
}

fun apiBaseUrlFor(config: ServerConfigData): String {
    val scheme = if (config.httpsEnabled) "https" else "http"
    return "$scheme://${config.serverIp}:${config.apiPort}/api"
}

private fun networkFailureToResolveResult(
    e: Throwable?,
    cached: String,
    allowCachedOnFailure: Boolean,
): InstanceIdResolveResult = when (e) {
    is TimeoutCancellationException,
    is HttpRequestTimeoutException,
    is SocketTimeoutException,
    -> {
        if (allowCachedOnFailure && cached.isNotEmpty() && isValidInstanceUuid(cached)) {
            InstanceIdResolveResult.Cached(cached)
        } else {
            InstanceIdResolveResult.Timeout
        }
    }
    is ConnectTimeoutException -> {
        if (allowCachedOnFailure && cached.isNotEmpty() && isValidInstanceUuid(cached)) {
            InstanceIdResolveResult.Cached(cached)
        } else {
            InstanceIdResolveResult.Unreachable
        }
    }
    is ClientRequestException -> {
        if (e.response.status.value in 400..499) {
            InstanceIdResolveResult.Unsupported
        } else if (allowCachedOnFailure && cached.isNotEmpty() && isValidInstanceUuid(cached)) {
            InstanceIdResolveResult.Cached(cached)
        } else {
            InstanceIdResolveResult.Unreachable
        }
    }
    else -> {
        if (allowCachedOnFailure && cached.isNotEmpty() && isValidInstanceUuid(cached)) {
            InstanceIdResolveResult.Cached(cached)
        } else {
            InstanceIdResolveResult.Unreachable
        }
    }
}
