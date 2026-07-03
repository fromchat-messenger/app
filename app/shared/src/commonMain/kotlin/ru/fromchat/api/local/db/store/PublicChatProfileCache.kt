package ru.fromchat.api.local.db.store

import ru.fromchat.api.schema.chats.publicchat.PublicChatProfile

/**
 * In-memory cache of the server-provided public chat profile (title, bio, member count).
 */
object PublicChatProfileCache {
    var profile: PublicChatProfile? = null
        private set

    fun put(profile: PublicChatProfile) {
        this.profile = profile
    }

    fun clear() {
        profile = null
    }
}
