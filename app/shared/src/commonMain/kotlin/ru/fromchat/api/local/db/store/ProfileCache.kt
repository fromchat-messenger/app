package ru.fromchat.api.local.db.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.fromchat.Logger
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.messages.Message
import ru.fromchat.api.schema.messages.dm.DmConversationUser
import ru.fromchat.api.schema.user.User
import ru.fromchat.api.schema.user.profile.UserProfile
import ru.fromchat.api.schema.user.profile.VerificationStatus
import ru.fromchat.api.local.cache.CacheContext
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Returns true when a profile entry should not expose its `username` field
 * to the UI (for suspended or deleted users), except for the current user.
 */
fun UserProfile.shouldHideUsername(currentUserId: Int? = null): Boolean =
    id != currentUserId && (
        deleted == true ||
            isDeletedPlaceholderUsername(username) ||
            (suspended == true && currentUserId != 1)
        )

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

    /** Epoch millis when a full (non-preview) profile was last applied from the server. */
    @Volatile
    private var fullProfileFetchedAtMs: Map<Int, Long> = emptyMap()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val persistMutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Skip force=false network refetch when a full profile was fetched within this window. */
    const val FULL_PROFILE_TTL_MS: Long = 5 * 60 * 1000L

    private fun bumpRevision(reason: String) {
        val next = _revision.value + 1
        _revision.value = next
        Logger.d("ProfileCache", "revision=$next reason=$reason size=${profiles.size}")
    }

    private fun profileSummary(profile: UserProfile): String =
        "id=${profile.id} user='${profile.username}' deleted=${profile.deleted} " +
            "suspended=${profile.suspended} preview=${profile.isClientPreviewOnly} " +
            "verified=${profile.verified} vStatus=${profile.verificationStatus}"

    fun get(userId: Int): UserProfile? = profiles[userId]

    /** True when a full non-preview profile was fetched recently enough to skip refetch. */
    @OptIn(ExperimentalTime::class)
    fun hasFreshFullProfile(userId: Int, maxAgeMs: Long = FULL_PROFILE_TTL_MS): Boolean {
        val profile = get(userId) ?: run {
            Logger.d("ProfileCache", "hasFreshFullProfile id=$userId miss")
            return false
        }
        if (profile.isClientPreviewOnly) {
            Logger.d("ProfileCache", "hasFreshFullProfile id=$userId stale=previewOnly")
            return false
        }
        val fetchedAt = fullProfileFetchedAtMs[userId] ?: run {
            Logger.d("ProfileCache", "hasFreshFullProfile id=$userId stale=noFetchTs")
            return false
        }
        val ageMs = Clock.System.now().toEpochMilliseconds() - fetchedAt
        val fresh = ageMs <= maxAgeMs
        Logger.d(
            "ProfileCache",
            "hasFreshFullProfile id=$userId fresh=$fresh ageMs=$ageMs maxAgeMs=$maxAgeMs",
        )
        return fresh
    }

    /** Emits whenever this user's cached profile changes (including bio). */
    fun observeUser(userId: Int): Flow<UserProfile?> =
        revision.map { get(userId) }.distinctUntilChanged()

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
        if (existing != null && !existing.isClientPreviewOnly) {
            Logger.d(
                "ProfileCache",
                "mergePreview skipFullExists id=$id deleted=${existing.deleted}",
            )
            return
        }

        val incomingUsername = username?.trim()?.takeIf { it.isNotEmpty() }
            ?: existing?.username?.trim()?.takeIf { it.isNotEmpty() }
        val isDeleted = existing?.deleted == true || isDeletedPlaceholderUsername(incomingUsername)
        val incomingDisplayName = if (isDeleted) {
            null
        } else {
            displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: existing?.displayName?.takeIf { it.isNotBlank() }
        }

        if (!isDeleted && incomingUsername.isNullOrEmpty() && incomingDisplayName.isNullOrBlank()) {
            Logger.d("ProfileCache", "mergePreview skipEmptyIdentity id=$id")
            return
        }

        if (incomingUsername.isNullOrEmpty() || incomingDisplayName.isNullOrBlank()) {
            Logger.d(
                "ProfileCache",
                "mergePreview missingIdentity id=$id " +
                    "hasUsername=${!incomingUsername.isNullOrEmpty()} " +
                    "hasDisplayName=${!incomingDisplayName.isNullOrBlank()}",
            )
        }

        Logger.d(
            "ProfileCache",
            "mergePreview id=$id deleted=$isDeleted hadExisting=${existing != null} " +
                "user='${incomingUsername.orEmpty()}' display='${incomingDisplayName.orEmpty()}'",
        )
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
                Logger.d("ProfileCache", "put removeEmptyPreview id=${profile.id}")
                remove(profile.id)
                return
            }
        }
        val cur = profiles
        val existing = cur[profile.id]
        val deletedChanged = existing?.deleted != profile.deleted
        val suspendedChanged = existing?.suspended != profile.suspended
        val verificationChanged = existing?.verified != profile.verified ||
            existing?.verificationStatus != profile.verificationStatus
        if (
            existing != null &&
            !existing.isClientPreviewOnly &&
            existing.bio != profile.bio
        ) {
            Logger.d(
                "ProfileCache",
                "put overwrite id=${profile.id} bio '${existing.bio?.take(48)}' -> " +
                    "'${profile.bio?.take(48)}' preview=${profile.isClientPreviewOnly}",
            )
        }
        if (deletedChanged || suspendedChanged || verificationChanged || existing == null) {
            Logger.d(
                "ProfileCache",
                "put ${profileSummary(profile)} hadExisting=${existing != null} " +
                    "deletedChanged=$deletedChanged suspendedChanged=$suspendedChanged " +
                    "verificationChanged=$verificationChanged",
            )
        }
        profiles = cur + (profile.id to profile)
        bumpRevision("put:${profile.id}")
        val instanceId = loadedInstanceId
        if (instanceId.isNotEmpty()) {
            ioScope.launch {
                runCatching { ProfileCacheStore.put(instanceId, profile) }
                    .onFailure {
                        Logger.w(
                            "ProfileCache",
                            "persist put failed id=${profile.id}: ${it.message}",
                            it,
                        )
                    }
            }
        }
    }

    /**
     * Applies a full server profile payload (HTTP or WebSocket).
     * When [force] is false, an existing full (non-preview) cache row is kept so a slow HTTP
     * response cannot overwrite a fresher WebSocket update.
     */
    @OptIn(ExperimentalTime::class)
    fun applyServerProfile(profile: UserProfile, force: Boolean = false) {
        if (profile.id <= 0) return
        val normalized = profile.copy(isClientPreviewOnly = false)
        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (!force) {
            val existing = get(profile.id)
            if (existing != null && !existing.isClientPreviewOnly) {
                val lifecycleMismatch =
                    existing.deleted != normalized.deleted ||
                        existing.suspended != normalized.suspended ||
                        isDeletedPlaceholderUsername(existing.username) !=
                        isDeletedPlaceholderUsername(normalized.username)
                if (lifecycleMismatch) {
                    Logger.w(
                        "ProfileCache",
                        "applyServerProfile force=false lifecycleMismatch " +
                            "cachedDeleted=${existing.deleted} incomingDeleted=${normalized.deleted} " +
                            "cachedSuspended=${existing.suspended} " +
                            "incomingSuspended=${normalized.suspended} " +
                            "cachedUser='${existing.username}' incomingUser='${normalized.username}' " +
                            "— applying full server profile",
                    )
                    put(normalized)
                    fullProfileFetchedAtMs = fullProfileFetchedAtMs + (profile.id to nowMs)
                    return
                }
                val patched = existing.copy(
                    verified = normalized.verified ?: existing.verified,
                    verificationStatus = normalized.verificationStatus
                        ?: existing.verificationStatus,
                )
                val verificationChanged = patched.verified != existing.verified ||
                    patched.verificationStatus != existing.verificationStatus
                if (patched != existing) put(patched)
                if (existing.bio != normalized.bio) {
                    Logger.d(
                        "ProfileCache",
                        "applyServerProfile skipped stale HTTP id=${profile.id} " +
                            "cachedBio='${existing.bio?.take(48)}' httpBio='${normalized.bio?.take(48)}' " +
                            "deleted=${existing.deleted}",
                    )
                } else {
                    Logger.d(
                        "ProfileCache",
                        "applyServerProfile keepCached force=false id=${profile.id} " +
                            "deleted=${existing.deleted} verificationChanged=$verificationChanged",
                    )
                }
                if (!verificationChanged) {
                    fullProfileFetchedAtMs = fullProfileFetchedAtMs + (profile.id to nowMs)
                }
                return
            }
        }
        Logger.d(
            "ProfileCache",
            "applyServerProfile applied force=$force ${profileSummary(normalized)} " +
                "bio='${normalized.bio?.take(48)}'",
        )
        put(normalized)
        fullProfileFetchedAtMs = fullProfileFetchedAtMs + (profile.id to nowMs)
    }

    fun remove(userId: Int) {
        val cur = profiles
        if (userId !in cur) {
            Logger.d("ProfileCache", "remove miss id=$userId")
            return
        }
        Logger.d("ProfileCache", "remove id=$userId wasDeleted=${cur[userId]?.deleted}")
        profiles = cur - userId
        bumpRevision("remove:$userId")
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

    fun mergeFromDmUser(user: DmConversationUser) {
        if (user.id <= 0) return

        val incomingUsername = user.username.trim()
        if (incomingUsername.isEmpty()) {
            Logger.d("ProfileCache", "mergeFromDmUser skipEmptyUsername id=${user.id}")
            return
        }

        val isDeleted = user.deleted == true || isDeletedPlaceholderUsername(incomingUsername)
        val incomingDisplayName = if (isDeleted) {
            null
        } else {
            user.displayName?.trim()?.takeIf { it.isNotEmpty() }
        }

        if (!isDeleted && incomingDisplayName.isNullOrBlank()) {
            Logger.d(
                "ProfileCache",
                "mergeFromDmUser missingDisplayName id=${user.id} user='$incomingUsername'",
            )
        }

        val existing = get(user.id)
        Logger.d(
            "ProfileCache",
            "mergeFromDmUser id=${user.id} deleted=$isDeleted " +
                "incomingDeleted=${user.deleted} hadFull=${existing != null && existing.isClientPreviewOnly != true} " +
                "user='$incomingUsername' display='${incomingDisplayName.orEmpty()}'",
        )
        if (existing != null && !existing.isClientPreviewOnly) {
            val patched = existing.copy(
                username = incomingUsername,
                displayName = if (isDeleted) {
                    null
                } else {
                    incomingDisplayName ?: existing.displayName
                },
                profilePicture = if (isDeleted) {
                    null
                } else {
                    user.profile_picture?.takeIf { it.isNotBlank() } ?: existing.profilePicture
                },
                online = user.online ?: existing.online,
                lastSeen = user.last_seen?.takeIf { it.isNotBlank() } ?: existing.lastSeen,
                verified = user.verified ?: existing.verified,
                verificationStatus = user.verificationStatus ?: existing.verificationStatus,
                suspended = user.suspended ?: existing.suspended,
                suspensionReason = user.suspensionReason ?: existing.suspensionReason,
                deleted = isDeleted,
            )
            if (patched != existing) {
                Logger.d(
                    "ProfileCache",
                    "mergeFromDmUser patchFull id=${user.id} " +
                        "deleted ${existing.deleted}→${patched.deleted}",
                )
                put(patched)
            }
            return
        }

        put(
            UserProfile(
                id = user.id,
                username = incomingUsername,
                displayName = if (isDeleted) {
                    null
                } else {
                    incomingDisplayName ?: existing?.displayName?.takeIf { it.isNotBlank() }
                },
                profilePicture = if (isDeleted) null else user.profile_picture?.takeIf { it.isNotBlank() }
                    ?: existing?.profilePicture,
                bio = existing?.bio,
                online = user.online ?: existing?.online ?: false,
                lastSeen = user.last_seen?.takeIf { it.isNotBlank() } ?: existing?.lastSeen,
                createdAt = existing?.createdAt,
                verified = user.verified ?: existing?.verified,
                verificationStatus = user.verificationStatus ?: existing?.verificationStatus,
                suspended = user.suspended ?: existing?.suspended,
                suspensionReason = user.suspensionReason ?: existing?.suspensionReason,
                deleted = isDeleted,
                isClientPreviewOnly = true,
            ),
        )
    }

    fun mergeFromUser(user: User) {
        mergeFromDmUser(
            DmConversationUser(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
                profile_picture = user.profile_picture,
                online = user.online,
                last_seen = user.last_seen,
                verified = user.verified,
                verificationStatus = user.verificationStatus,
                suspended = user.suspended,
                suspensionReason = user.suspensionReason,
                deleted = user.deleted,
            ),
        )
    }

    fun mergePreviewFromPublicMessage(message: Message) {
        val uid = message.user_id
        if (uid <= 0) return
        val existing = get(uid)
        val incomingDisplay = message.displayName?.trim()?.takeIf { it.isNotEmpty() }
        val incomingPic = message.profile_picture?.takeIf { it.isNotBlank() }
        if (existing != null && !existing.isClientPreviewOnly) {
            val patched = existing.copy(
                verified = message.verified ?: existing.verified,
                verificationStatus = message.verificationStatus ?: existing.verificationStatus,
                displayName = existing.displayName?.takeIf { it.isNotBlank() } ?: incomingDisplay,
                profilePicture = existing.profilePicture?.takeIf { it.isNotBlank() } ?: incomingPic,
                username = existing.username.trim().ifBlank { message.username.trim() },
            )
            if (patched != existing) put(patched)
            return
        }

        val uname = message.username.trim().ifBlank { existing?.username?.trim().orEmpty() }
        val displayName = incomingDisplay ?: existing?.displayName?.takeIf { it.isNotBlank() }
        if (uname.isBlank() && displayName.isNullOrBlank()) return
        if (uname.isBlank() || displayName.isNullOrBlank()) {
            Logger.d(
                "ProfileCache",
                "mergePreviewFromPublicMessage missingIdentity id=$uid " +
                    "hasUsername=${uname.isNotBlank()} hasDisplayName=${!displayName.isNullOrBlank()}",
            )
        }
        val isDeleted = isDeletedPlaceholderUsername(uname) || existing?.deleted == true
        val display = if (isDeleted) null else displayName
        val pic = if (isDeleted) null else incomingPic ?: existing?.profilePicture

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
        val enrichedReply = message.reply_to?.let { enrichPublicMessageForDisplay(it, currentUserId) }
        val self = currentUserId
        if (self != null && message.user_id == self) {
            val user = ApiClient.user
            return message.copy(
                username = message.username.trim().ifBlank { user?.username.orEmpty() },
                displayName = message.displayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: user?.displayName?.trim()?.takeIf { it.isNotEmpty() },
                profile_picture = message.profile_picture?.takeIf { it.isNotBlank() }
                    ?: user?.profile_picture,
                reply_to = enrichedReply,
            )
        }
        val profile = get(message.user_id)
        return message.copy(
            username = message.username.trim().ifBlank {
                profile?.visibleUsername(self).orEmpty()
            },
            displayName = message.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: profile?.displayName?.trim()?.takeIf { it.isNotEmpty() },
            profile_picture = message.profile_picture?.takeIf { it.isNotBlank() }
                ?: profile?.profilePicture,
            verified = message.verified ?: profile?.verified,
            verificationStatus = message.verificationStatus ?: profile?.verificationStatus,
            reply_to = enrichedReply,
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
                fullProfileFetchedAtMs = emptyMap()
                pruneUnusableClientPreviewsLocked()
                bumpRevision("onActiveInstanceChanged:$instanceId")
            }
        }
    }

    suspend fun hydrateFromDisk() {
        val instanceId = CacheContext.activeInstanceId.value.trim()
        persistMutex.withLock {
            loadedInstanceId = instanceId
            val diskProfiles = if (instanceId.isNotEmpty()) {
                runCatching { ProfileCacheStore.loadAllForInstance(instanceId) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            val before = profiles.size
            if (profiles.isEmpty()) {
                profiles = diskProfiles
            } else {
                val merged = profiles.toMutableMap()
                for ((userId, diskProfile) in diskProfiles) {
                    val inMemory = merged[userId]
                    when {
                        inMemory == null -> merged[userId] = diskProfile
                        inMemory.isClientPreviewOnly && !diskProfile.isClientPreviewOnly ->
                            merged[userId] = diskProfile
                        // Keep in-memory full profiles over disk — disk may lag behind WS.
                    }
                }
                profiles = merged
            }
            val deletedCount = profiles.values.count { it.deleted == true }
            Logger.d(
                "ProfileCache",
                "hydrateFromDisk instanceId=$instanceId before=$before " +
                    "disk=${diskProfiles.size} after=${profiles.size} deletedCount=$deletedCount",
            )
            pruneUnusableClientPreviewsLocked()
            bumpRevision("hydrateFromDisk")
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
        Logger.d("ProfileCache", "pruneUnusablePreviews ids=$toRemove")
        var cur = profiles
        for (id in toRemove) {
            cur = cur - id
        }
        profiles = cur
    }

    suspend fun clear() {
        persistMutex.withLock {
            Logger.d("ProfileCache", "clear sizeWas=${profiles.size}")
            profiles = emptyMap()
            fullProfileFetchedAtMs = emptyMap()
            loadedInstanceId = ""
            bumpRevision("clear")
        }
    }
}