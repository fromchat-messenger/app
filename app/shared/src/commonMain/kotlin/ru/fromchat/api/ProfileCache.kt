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
 * Returns true when a profile entry should not expose its `username` field
 * to the UI (for suspended or deleted users), except for the current user.
 */
fun UserProfile.shouldHideUsername(currentUserId: Int? = null): Boolean =
    id != currentUserId && (deleted == true || suspended == true)

fun UserProfile.visibleUsername(currentUserId: Int? = null): String? =
    if (shouldHideUsername(currentUserId)) {
        null
    } else {
        username.trim().takeIf { it.isNotBlank() }
    }

fun UserProfile.visibleDisplayName(currentUserId: Int? = null): String? =
    displayName?.trim()?.ifEmpty { null } ?: visibleUsername(currentUserId)

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
        if (profile.isClientPreviewOnly) {
            val hasIdentity =
                profile.username.trim().isNotEmpty() || !profile.displayName.isNullOrBlank()
            if (!hasIdentity) {
                remove(profile.id)
                return
            }
        }
        val cur = profiles
        profiles = cur + (profile.id to profile)
        schedulePersist()
    }

    /**
     * Removes one user from the cache and persists. Does not clear full API profiles unless
     * the caller only invokes this for preview cleanup flows.
     */
    fun remove(userId: Int) {
        val cur = profiles
        if (userId !in cur) return
        profiles = cur - userId
        schedulePersist()
    }

    /**
     * Drops [isClientPreviewOnly] entries that have no usable identity (blank username and
     * display name). Avoids persisting or showing stale rows from failed loads / bad merges.
     */
    fun evictUnusableClientPreview(userId: Int) {
        val p = get(userId) ?: return
        if (!p.isClientPreviewOnly) return
        val hasIdentity =
            p.username.trim().isNotEmpty() || !p.displayName.isNullOrBlank()
        if (!hasIdentity) remove(userId)
    }

    /**
     * Seeds or refreshes a lightweight profile from a DM conversations list [User].
     * Skips when a full `/user/...` profile is already cached.
     * Does not write a preview when the API user has no non-blank username (avoids caching empty identity).
     */
    fun mergeFromDmUser(user: User) {
        val existing = get(user.id)
        if (existing != null && !existing.isClientPreviewOnly) return

        val incomingUsername = user.username.trim()
        if (incomingUsername.isEmpty()) return

        val incomingDisplayName =
            user.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: incomingUsername

        put(
            UserProfile(
                id = user.id,
                username = incomingUsername,
                displayName = existing?.displayName?.takeIf { it.isNotBlank() } ?: incomingDisplayName,
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
                val usable = list.filter { p ->
                    when {
                        !p.isClientPreviewOnly -> true
                        else ->
                            p.username.trim().isNotEmpty() ||
                                !p.displayName.isNullOrBlank()
                    }
                }
                val fromDisk = usable.associateBy { it.id }
                profiles = fromDisk + profiles
                if (usable.size < list.size) {
                    schedulePersist()
                }
            }
            pruneUnusableClientPreviewsLocked()
        }
    }

    /**
     * Drops in-memory preview-only rows with no identity (e.g. bad runtime state after a failed merge).
     * Call only while holding [persistMutex] or from [hydrateFromDisk] inside the lock.
     */
    private fun pruneUnusableClientPreviewsLocked() {
        val snap = profiles
        val toRemove = snap.filter { (_, p) ->
            p.isClientPreviewOnly &&
                p.username.trim().isEmpty() &&
                p.displayName.isNullOrBlank()
        }.keys
        if (toRemove.isEmpty()) return
        var cur = profiles
        for (id in toRemove) {
            cur = cur - id
        }
        profiles = cur
        schedulePersist()
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
