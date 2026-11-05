package ru.fromchat.api

import kotlinx.serialization.Serializable
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import ru.fromchat.api.schema.core.ConflictErrorDetail

const val ENCRYPTION_MODE_VERSION_STALE = "encryption_mode_version_stale"
const val WRONG_ENCRYPTION_MODE = "wrong_encryption_mode"

class EncryptionPolicyConflictException(
    val code: String,
    override val message: String,
) : Exception(message)

private val conflictJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class ConflictErrorWrapper(val detail: ConflictErrorDetail)

suspend fun ClientRequestException.encryptionPolicyConflictOrNull(): EncryptionPolicyConflictException? {
    if (response.status != HttpStatusCode.Conflict) return null
    return runCatching {
        val wrapper = response.body<ConflictErrorWrapper>()
        val detail = wrapper.detail
        if (detail.code.isBlank()) return null
        EncryptionPolicyConflictException(detail.code, detail.message.ifBlank { detail.code })
    }.getOrNull()
}

suspend fun Throwable.encryptionPolicyConflictOrNull(): EncryptionPolicyConflictException? =
    (this as? ClientRequestException)?.encryptionPolicyConflictOrNull()
