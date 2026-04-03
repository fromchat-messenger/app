package ru.fromchat.api

import com.pr0gramm3r101.utils.settings.settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

/**
 * In-memory profile cache with disk persistence. [get] reads a volatile snapshot (lock-free).
 * [put] copy-on-writes the map and schedules an async flush of the full list to settings.
 */
object ProfileCache {
    private const val SETTINGS_KEY = "profile_cache_profiles_v1"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Volatile
    private var profiles: Map<Int, UserProfile> = emptyMap()

    private val persistMutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun get(userId: Int): UserProfile? = profiles[userId]

    fun put(profile: UserProfile) {
        val cur = profiles
        profiles = cur + (profile.id to profile)
        schedulePersist()
    }

    /**
     * Fills or refreshes a lightweight profile from a public chat [Message] (username, avatar URL).
     * Skips when a full API profile is already stored ([UserProfile.isClientPreviewOnly] is false).
     */
    /**
     * Seeds or refreshes a lightweight profile from a DM conversations list [User].
     * Skips when a full `/user/...` profile is already cached.
     */
    fun mergeFromDmUser(user: User) {
        val existing = get(user.id)
        if (existing != null && !existing.isClientPreviewOnly) return
        put(
            UserProfile(
                id = user.id,
                username = user.username.ifBlank { existing?.username.orEmpty() },
                displayName = existing?.displayName?.takeIf { it.isNotBlank() }
                    ?: user.username.takeIf { it.isNotBlank() }
                    ?: existing?.username.orEmpty(),
                profilePicture = user.profile_picture?.takeIf { it.isNotBlank() }
                    ?: existing?.profilePicture,
                bio = existing?.bio,
                online = user.online,
                lastSeen = user.last_seen.takeIf { it.isNotBlank() } ?: existing?.lastSeen,
                createdAt = user.created_at.takeIf { it.isNotBlank() } ?: existing?.createdAt,
                verified = existing?.verified,
                suspended = existing?.suspended,
                suspensionReason = existing?.suspensionReason,
                deleted = existing?.deleted,
                isClientPreviewOnly = true
            )
        )
    }

    fun mergePreviewFromPublicMessage(message: Message) {
        val uid = message.user_id
        if (uid <= 0) return
        val existing = get(uid)
        if (existing != null && !existing.isClientPreviewOnly) return

        val uname = message.username.ifBlank { existing?.username ?: return }
        val display = existing?.displayName?.takeIf { it.isNotBlank() } ?: uname
        val pic = message.profile_picture?.takeIf { it.isNotBlank() } ?: existing?.profilePicture

        put(
            UserProfile(
                id = uid,
                username = uname,
                displayName = display,
                profilePicture = pic,
                bio = existing?.bio,
                online = existing?.online ?: false,
                lastSeen = existing?.lastSeen,
                createdAt = existing?.createdAt,
                verified = existing?.verified,
                suspended = existing?.suspended,
                suspensionReason = existing?.suspensionReason,
                deleted = existing?.deleted,
                isClientPreviewOnly = true
            )
        )
    }

    /**
     * Load stored profiles into memory (merged: existing runtime entries win on id clash).
     * Call after [ApiClient.loadPersistedData].
     */
    suspend fun hydrateFromDisk() {
        persistMutex.withLock {
            val raw = settings.getString(SETTINGS_KEY, "").ifBlank { return }
            runCatching {
                val list = json.decodeFromString(ListSerializer(UserProfile.serializer()), raw)
                val fromDisk = list.associateBy { it.id }
                profiles = fromDisk + profiles
            }
        }
    }

    suspend fun clear() {
        persistMutex.withLock {
            profiles = emptyMap()
            settings.putString(SETTINGS_KEY, "")
        }
    }

    private fun schedulePersist() {
        ioScope.launch {
            persistMutex.withLock {
                val snap = profiles
                val blob = json.encodeToString(
                    ListSerializer(UserProfile.serializer()),
                    snap.values.toList()
                )
                settings.putString(SETTINGS_KEY, blob)
            }
        }
    }
}
