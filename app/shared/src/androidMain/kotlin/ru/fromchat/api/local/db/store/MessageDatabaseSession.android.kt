package ru.fromchat.api.local.db.store

internal actual fun closeMessageDatabaseForIncognitoSession() {
    MessageDatabaseProvider.closeAndReset()
}
