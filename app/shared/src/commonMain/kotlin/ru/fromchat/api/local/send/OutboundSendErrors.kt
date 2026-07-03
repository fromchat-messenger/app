package ru.fromchat.api.local.send

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.TimeoutCancellationException

/** Error key stored on [ru.fromchat.api.schema.messages.Message.uploadError] for localized send-failure UI. */
const val SEND_ERROR_FAILED = "send_failed"

/** HTTP status codes that mark a permanent send failure (show error, offer retry/cancel). */
private val permanentHttpStatuses = setOf(400, 401, 403, 404, 422, 500)

fun Throwable.isOutboundPermanentFailure(): Boolean = when (this) {
    is ClientRequestException -> response.status.value in permanentHttpStatuses
    else -> false
}

/** Transient failures: keep pending and retry (no network, timeouts, other server errors). */
fun Throwable.isOutboundTransientFailure(): Boolean = when {
    isOutboundPermanentFailure() -> false
    this is TimeoutCancellationException ||
        this is HttpRequestTimeoutException ||
        this is SocketTimeoutException ||
        this is ConnectTimeoutException -> true
    this is ClientRequestException -> true
    else -> true
}

fun outboundFailureErrorKey(error: Throwable): String = SEND_ERROR_FAILED
