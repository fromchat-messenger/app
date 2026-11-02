package ru.fromchat.incognito

import ru.fromchat.api.local.db.store.MessageDatabaseProvider
import ru.fromchat.logging.AppLogStore

internal fun buildIncognitoContainerExtras(): List<Pair<String, ByteArray>> = buildList {
    addAll(AppLogStore.incognitoSnapshotFileEntries())
    MessageDatabaseProvider.exportIncognitoMessageDatabaseSnapshot()?.let { bytes ->
        add("fromchat/message_database.db" to bytes)
    }
}
