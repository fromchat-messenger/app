package ru.fromchat.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType

actual fun createPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient {
    return HttpClient(CIO) {
        defaultRequest {
            contentType(ContentType.Application.Json)

            headers {
                append("Accept-Charset", "utf-8")
                append("Content-Type", "application/json; charset=utf-8")
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 30_000
        }

        block(this)
    }
}
