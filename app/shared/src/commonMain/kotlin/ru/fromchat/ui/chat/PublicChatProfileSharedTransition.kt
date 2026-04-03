package ru.fromchat.ui.chat

/**
 * Stable shared-element keys for NavHost predictive back–compatible transitions from
 * a public-chat message row to [ru.fromchat.ui.profile.ProfileScreen].
 * One key per message so LazyColumn rows never duplicate keys for the same user.
 */
fun publicChatProfileSharedAvatarKey(userId: Int, sourceMessageId: Int): String =
    "public-chat-profile-avatar-$userId-$sourceMessageId"
