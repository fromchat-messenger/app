package ru.fromchat.api.local.db.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.user.User
import ru.fromchat.api.schema.user.profile.UserProfile
import ru.fromchat.api.schema.user.profile.VerificationStatus
import ru.fromchat.api.local.cache.CacheContext
import kotlin.concurrent.Volatile

/**
 * Returns true when a profile entry should not expose its `username` field
 * to the UI (for suspended or deleted users), except for the current user.
 */
fun UserProfile.shouldHideUsername(currentUserId: Int? = null): Boolean =
    id != currentUserId && (deleted == true || isDeletedPlaceholderUsername(username))

private fun isDeletedPlaceholderUsername(username: String?): Boolean =
    username?.startsWith("#deleted") == true

fun UserProfile.visibleUsername(currentUserId: Int? = null): String? =
    if (shouldHideUsername(currentUserId)) {
        null
    } else {
        username.trim().takeIf { it.isNotBlank() }
    }

fun UserProfile.visibleDisplayName(currentUserId: Int? = null): String? =
    displayName?.trim()?.ifEmpty { null } ?: visibleUsername(currentUserId)

/**
 * In-memory profile cache for the active [CacheContext] instance,
 * backed by SQLDelight [profile_cache] per instance partition.
 */
object ProfileCache {
    @Volatile
    private var profiles: Map<Int, UserProfile> = emptyMap()

    @Volatile
    private var loadedInstanceId: String = ""

    private val persistMutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun get(userId: Int): UserProfile? = profiles[userId]

    fun findByUsername(username: String): UserProfile? =
        username.trim().takeIf { it.isNotEmpty() }?.let { needle ->
            profiles.values.firstOrNull { profile ->
                profile.username.trim().equals(needle, ignoreCase = true)
            }
        }

    /**
     * Merges minimal identity (name, avatar) from any UI surface that showed this user.
     * Skips when a full profile row is already cached.
     */
    fun mergePreview(
        id: Int,
        username: String? = null,
        displayName: String? = null,
        profilePicture: String? = null,
        verificationStatus: VerificationStatus? = null,
    ) {
        if (id <= 0) return
        val existing = get(id)
        if (existing != null && !existing.isClientPreviewOnly) return

        val incomingUsername = username?.trim()?.takeIf { it.isNotEmpty() }
            ?: existing?.username?.trim()?.takeIf { it.isNotEmpty() }
        val isDeleted = existing?.deleted == true || isDeletedPlaceholderUsername(incomingUsername)
        val incomingDisplayName = if (isDeleted) {
            null
        } else {
            displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: existing?.displayName?.takeIf { it.isNotBlank() }
                ?: incomingUsername
        }

        if (!isDeleted && incomingUsername.isNullOrEmpty() && incomingDisplayName.isNullOrBlank()) return

        put(
            UserProfile(
                id = id,
                username = incomingUsername.orEmpty(),
                displayName = incomingDisplayName,
                profilePicture = if (isDeleted) null else profilePicture?.takeIf { it.isNotBlank() }
                    ?: existing?.profilePicture,
                bio = existing?.bio,
                online = existing?.online ?: false,
                lastSeen = existing?.lastSeen,
                createdAt = existing?.createdAt,
                verified = existing?.verified,
                verificationStatus = verificationStatus ?: existing?.verificationStatus,
                suspended = existing?.suspended,
                suspensionReason = existing?.suspensionReason,
                deleted = isDeleted,
                isClientPreviewOnly = true,
            ),
        )
    }

    fun mergeFromCachedConversation(conversation: CachedConversation) {
        if (conversation.otherUserId <= 0) return
        mergePreview(
            id = conversation.otherUserId,
            displayName = conversation.displayName.takeIf { it.isNotBlank() },
        )
    }

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
        val instanceId = loadedInstanceId
        if (instanceId.isNotEmpty()) {
            ioScope.launch {
                runCatching { ProfileCacheStore.put(instanceId, profile) }
            }
        }
    }

    fun remove(userId: Int) {
        val cur = profiles
        if (userId !in cur) return
        profiles = cur - userId
        val instanceId = loadedInstanceId
        if (instanceId.isNotEmpty()) {
            ioScope.launch {
                runCatching { ProfileCacheStore.remove(instanceId, userId) }
            }
        }
    }

    fun evictUnusableClientPreview(userId: Int) {
        val p = get(userId) ?: return
        if (!p.isClientPreviewOnly) return
        val hasIdentity =
            p.username.trim().isNotEmpty() || !p.displayName.isNullOrBlank()
        if (!hasIdentity) remove(userId)
    }

    fun mergeFromDmUser(user: User) {
        val existing = get(user.id)
        if (existing != null && !existing.isClientPreviewOnly) return

        val incomingUsername = user.username.trim()
        if (incomingUsername.isEmpty()) return

        val isDeleted = user.deleted == true || isDeletedPlaceholderUsername(incomingUsername)
        val incomingDisplayName = if (isDeleted) {
            null
        } else {
            user.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: incomingUsername
        }

        put(
            UserProfile(
                id = user.id,
                username = incomingUsername,
                displayName = if (isDeleted) null else existing?.displayName?.takeIf { it.isNotBlank() }
                    ?: incomingDisplayName,
                profilePicture = if (isDeleted) null else user.profile_picture?.takeIf { it.isNotBlank() }
                    ?: existing?.profilePicture,
                bio = existing?.bio,
                online = user.online,
                lastSeen = user.last_seen?.takeIf { it.isNotBlank() } ?: existing?.lastSeen,
                createdAt = user.created_at.takeIf { it.isNotBlank() } ?: existing?.createdAt,
                verified = user.verified ?: existing?.verified,
                verificationStatus = user.verificationStatus ?: existing?.verificationStatus,
                suspended = user.suspended ?: existing?.suspended,
                suspensionReason = user.suspensionReason ?: existing?.suspensionReason,
                deleted = isDeleted,
                isClientPreviewOnly = true,
            ),
        )
    }

    fun mergePreviewFromPublicMessage(message: Message) {
        val uid = message.user_id
        if (uid <= 0) return
        val existing = get(uid)
        if (existing != null && !existing.isClientPreviewOnly) return

        val uname = message.username.trim().ifBlank { existing?.username?.trim().orEmpty() }
        if (uname.isBlank()) return
        val isDeleted = isDeletedPlaceholderUsername(uname) || existing?.deleted == true
        val display = if (isDeleted) null else existing?.displayName?.takeIf { it.isNotBlank() } ?: uname
        val pic = if (isDeleted) null else message.profile_picture?.takeIf { it.isNotBlank() }
            ?: existing?.profilePicture

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
                verified = message.verified ?: existing?.verified,
                verificationStatus = message.verificationStatus ?: existing?.verificationStatus,
                suspended = existing?.suspended,
                suspensionReason = existing?.suspensionReason,
                deleted = isDeleted,
                isClientPreviewOnly = true,
            ),
        )
    }

    fun mergePreviewFromPublicMessages(messages: Iterable<Message>) {
        messages.forEach(::mergePreviewFromPublicMessage)
    }

    /**
     * Fills blank sender fields on a public-chat [Message] from [ProfileCache] or the current user.
     */
    fun enrichPublicMessageForDisplay(
        message: Message,
        currentUserId: Int? = ApiClient.user?.id,
    ): Message {
        val self = currentUserId
        if (self != null && message.user_id == self) {
            val user = ApiClient.user
            return message.copy(
                username = message.username.trim().ifBlank { user?.username.orEmpty() },
                profile_picture = message.profile_picture?.takeIf { it.isNotBlank() }
                    ?: user?.profile_picture,
            )
        }
        val profile = get(message.user_id)
        return message.copy(
            username = message.username.trim().ifBlank {
                profile?.visibleUsername(self).orEmpty()
            },
            profile_picture = message.profile_picture?.takeIf { it.isNotBlank() }
                ?: profile?.profilePicture,
            verified = message.verified ?: profile?.verified,
            verificationStatus = message.verificationStatus ?: profile?.verificationStatus,
        )
    }

    fun enrichPublicMessagesForDisplay(
        messages: List<Message>,
        currentUserId: Int? = ApiClient.user?.id,
    ): List<Message> = messages.map { enrichPublicMessageForDisplay(it, currentUserId) }

    fun onActiveInstanceChanged(instanceId: String) {
        ioScope.launch {
            persistMutex.withLock {
                loadedInstanceId = instanceId
                profiles = if (instanceId.isNotEmpty()) {
                    runCatching { ProfileCacheStore.loadAllForInstance(instanceId) }.getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }
                pruneUnusableClientPreviewsLocked()
            }
        }
    }

    suspend fun hydrateFromDisk() {
        val instanceId = CacheContext.activeInstanceId.value.trim()
        persistMutex.withLock {
            loadedInstanceId = instanceId
            profiles = if (instanceId.isNotEmpty()) {
                runCatching { ProfileCacheStore.loadAllForInstance(instanceId) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            pruneUnusableClientPreviewsLocked()
        }
    }

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
    }

    suspend fun clear() {
        persistMutex.withLock {
            profiles = emptyMap()
            loadedInstanceId = ""
        }
    }
}
