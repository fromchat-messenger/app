package ru.fromchat.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.Logger
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.db.store.MessageRepository
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.local.db.store.UserStatusStore
import ru.fromchat.api.schema.user.profile.UserProfile
import ru.fromchat.api.schema.user.profile.VerificationStatus
import ru.fromchat.api.schema.user.profile.orFromLegacyVerified
import ru.fromchat.api.schema.websocket.WebSocketMessage
import ru.fromchat.api.schema.websocket.types.WebSocketUpdatesData

/**
 * Applies [profileUpdate] WebSocket payloads to [ProfileCache], the current-user session,
 * and DM conversation list labels so profile changes reflect everywhere without a refetch.
 */
object ProfileUpdateSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun ensureStarted() {
        if (started) return
        started = true
        WebSocketManager.addGlobalMessageHandler(::handleWebSocketMessage)
    }

    fun resetOnLogout() {
        started = false
    }

    fun onProfileUpdatePayload(data: JsonElement) {
        scope.launch { applyProfileUpdate(data) }
    }

    private fun handleWebSocketMessage(message: WebSocketMessage) {
        when (message.type) {
            "updates" -> {
                val data = message.data ?: return
                val updates = runCatching {
                    ApiClient.json.decodeFromJsonElement(WebSocketUpdatesData.serializer(), data)
                }.getOrNull() ?: run {
                    Logger.w("ProfileUpdateSync", "updates batch decode failed")
                    return
                }
                val profileUpdates = updates.updates.count { it.type == "profileUpdate" }
                if (profileUpdates > 0) {
                    Logger.d(
                        "ProfileUpdateSync",
                        "updates batch seq=${updates.seq} profileUpdateCount=$profileUpdates",
                    )
                }
                updates.updates.forEach { update ->
                    handleWebSocketMessage(WebSocketMessage(type = update.type, data = update.data))
                }
            }
            "profileUpdate" -> {
                val payload = message.data ?: run {
                    Logger.w("ProfileUpdateSync", "profileUpdate missing data")
                    return
                }
                onProfileUpdatePayload(payload)
            }
        }
    }

    private suspend fun applyProfileUpdate(data: JsonElement) {
        val profile = parseProfileUpdate(data) ?: run {
            Logger.w("ProfileUpdateSync", "profileUpdate parse failed: ${data.toString().take(200)}")
            return
        }
        if (profile.id <= 0) return

        Logger.d(
            "ProfileUpdateSync",
            "profileUpdate id=${profile.id} username='${profile.username}' " +
                "deleted=${profile.deleted} suspended=${profile.suspended} " +
                "bio='${profile.bio?.take(48)}' revisionBefore=${ProfileCache.revision.value}",
        )
        val hadCached = ProfileCache.get(profile.id)
        ProfileCache.applyServerProfile(profile, force = true)
        val after = ProfileCache.get(profile.id)
        Logger.d(
            "ProfileUpdateSync",
            "profileUpdate applied id=${profile.id} " +
                "wasDeleted=${hadCached?.deleted} nowDeleted=${after?.deleted} " +
                "revisionAfter=${ProfileCache.revision.value}",
        )
        UserStatusStore.update(profile.id, profile.online, profile.lastSeen)

        if (ApiClient.user?.id == profile.id) {
            ApiClient.applyOwnProfile(profile)
        }

        runCatching { MessageRepository.patchDmConversationPeerProfile(profile.id) }
            .onFailure {
                Logger.w(
                    "ProfileUpdateSync",
                    "patchDmConversationPeerProfile failed id=${profile.id}: ${it.message}",
                    it,
                )
            }
            .onSuccess {
                Logger.d(
                    "ProfileUpdateSync",
                    "patchDmConversationPeerProfile done id=${profile.id}",
                )
            }
    }

    private fun parseProfileUpdate(data: JsonElement): UserProfile? {
        val normalized = normalizeProfilePayload(data)
        val decoded = runCatching {
            ApiClient.json.decodeFromJsonElement(UserProfile.serializer(), normalized)
        }.getOrElse { error ->
            Logger.w("ProfileUpdateSync", "UserProfile decode failed: ${error.message}", error)
            null
        }
        if (decoded != null) {
            return decoded.copy(isClientPreviewOnly = false)
        }
        return parseProfileUpdateFallback(data)
    }

    private fun normalizeProfilePayload(data: JsonElement): JsonElement {
        val obj = data.jsonObject
        val fixed = obj.toMutableMap()
        for (key in listOf("created_at", "last_seen")) {
            val value = obj[key] ?: continue
            if (value !is JsonPrimitive) {
                fixed[key] = JsonPrimitive(value.toString().trim('"'))
            }
        }
        return JsonObject(fixed)
    }

    private fun parseProfileUpdateFallback(data: JsonElement): UserProfile? {
        val obj = data.jsonObject
        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return null
        val verified = obj["verified"]?.jsonPrimitive?.booleanOrNull
        val verificationRaw = obj["verification_status"]?.jsonPrimitive?.contentOrNull
        val verificationStatus = verificationRaw?.let { raw ->
            VerificationStatus.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: when (raw.lowercase()) {
                    "verified" -> VerificationStatus.Verified
                    "warning" -> VerificationStatus.Warning
                    "blocked" -> VerificationStatus.Blocked
                    else -> VerificationStatus.None
                }
        }
        return UserProfile(
            id = id,
            username = obj["username"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            displayName = obj["display_name"]?.jsonPrimitive?.contentOrNull,
            profilePicture = obj["profile_picture"]?.jsonPrimitive?.contentOrNull,
            bio = obj["bio"]?.jsonPrimitive?.contentOrNull,
            online = obj["online"]?.jsonPrimitive?.booleanOrNull ?: false,
            lastSeen = jsonScalarAsString(obj["last_seen"]),
            createdAt = jsonScalarAsString(obj["created_at"]),
            verified = verified,
            verificationStatus = verificationStatus.orFromLegacyVerified(verified),
            suspended = obj["suspended"]?.jsonPrimitive?.booleanOrNull,
            suspensionReason = obj["suspension_reason"]?.jsonPrimitive?.contentOrNull,
            deleted = obj["deleted"]?.jsonPrimitive?.booleanOrNull,
            isClientPreviewOnly = false,
        )
    }

    private fun jsonScalarAsString(element: JsonElement?): String? {
        element ?: return null
        return element.jsonPrimitive.contentOrNull ?: element.toString().trim('"')
    }
}
