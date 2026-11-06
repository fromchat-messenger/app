package ru.fromchat.api.local.db.store

/** Closes the DB on the platform SQLite thread (JVM) before incognito storage routing changes. */
internal expect fun closeMessageDatabaseForIncognitoSession()
