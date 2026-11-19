package ru.fromchat.api.local.db

/**
 * Serializes first driver open + [ru.fromchat.api.local.db.ensureMessageDatabaseSchema] without
 * nesting [withMessageDatabaseLock] incorrectly (avoids JVM-only [synchronized] in commonMain).
 */
internal expect fun <T> withMessageDatabaseOpenMutex(block: () -> T): T
