package ru.fromchat.api

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import ru.fromchat.api.schema.core.ErrorResponse
import ru.fromchat.Logger

suspend inline fun <Response> apiRequest(
    unexpectedError: String,
    onError: (String, Exception) -> Unit = { _, _ -> },
    onSuccess: (Response) -> Unit = {},
    request: suspend () -> Response
): Result<Response> {
    try {
        val response = request()
        onSuccess(response)
        return Result.success(response)
    } catch (e: ClientRequestException) {
        val message = if (e.response.status.value in arrayOf(401, 403, 429)) {
            e.response.body<ErrorResponse>().detail
        } else {
            unexpectedError
        }

        Logger.e("API", "API request failed: $message", e)

        onError(message, e)
        return Result.failure(e)
    } catch (e: Exception) {
        Logger.e("API", "API request failed: ${e.message}", e)
        onError(unexpectedError, e)
        return Result.failure(e)
    }
}