package ru.fromchat.api.local.db

/**
 * Target SQLDelight schema (source of truth for diff). Not a version number — only shape.
 */
internal object MessageDatabaseExpectedSchema {
    val managedTables: Set<String> = setOf(
        "server_binding",
        "instance_registry",
        "conversation",
        "message",
        "attachment",
        "outbox",
        "profile_cache",
    )

    val tableCreateSql: Map<String, String> = mapOf(
        "server_binding" to """
            CREATE TABLE server_binding (
                configKey TEXT NOT NULL PRIMARY KEY,
                activeInstanceId TEXT NOT NULL,
                updatedAt TEXT
            )
        """.trimIndent(),
        "instance_registry" to """
            CREATE TABLE instance_registry (
                instanceId TEXT NOT NULL PRIMARY KEY,
                firstSeenAt TEXT NOT NULL,
                lastSeenAt TEXT NOT NULL
            )
        """.trimIndent(),
        "conversation" to """
            CREATE TABLE conversation (
                instanceId TEXT NOT NULL,
                id TEXT NOT NULL,
                type TEXT NOT NULL,
                otherUserId INTEGER,
                displayName TEXT,
                lastMessageId INTEGER,
                lastMessagePreview TEXT,
                unreadCount INTEGER NOT NULL DEFAULT 0,
                updatedAt TEXT,
                archived INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (instanceId, id)
            )
        """.trimIndent(),
        "message" to """
            CREATE TABLE message (
                instanceId TEXT NOT NULL,
                id INTEGER NOT NULL,
                conversationId TEXT NOT NULL,
                userId INTEGER NOT NULL,
                content TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                isRead INTEGER NOT NULL,
                isEdited INTEGER NOT NULL,
                replyToId INTEGER,
                clientMessageId TEXT,
                deletedFlag INTEGER NOT NULL DEFAULT 0,
                sendStatus TEXT,
                PRIMARY KEY (instanceId, conversationId, id)
            )
        """.trimIndent(),
        "attachment" to """
            CREATE TABLE attachment (
                instanceId TEXT NOT NULL,
                id INTEGER NOT NULL,
                messageId INTEGER NOT NULL,
                conversationId TEXT NOT NULL,
                remotePath TEXT,
                localPath TEXT,
                status TEXT NOT NULL,
                blurhash TEXT,
                aspectRatio REAL,
                size INTEGER,
                bytesTransferred INTEGER NOT NULL DEFAULT 0,
                clientMessageId TEXT,
                PRIMARY KEY (instanceId, id, messageId, conversationId)
            )
        """.trimIndent(),
        "outbox" to """
            CREATE TABLE outbox (
                instanceId TEXT NOT NULL,
                clientMessageId TEXT NOT NULL,
                conversationId TEXT NOT NULL,
                kind TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                retryCount INTEGER NOT NULL DEFAULT 0,
                nextAttemptAt TEXT,
                bytesUploaded INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (instanceId, clientMessageId)
            )
        """.trimIndent(),
        "profile_cache" to """
            CREATE TABLE profile_cache (
                instanceId TEXT NOT NULL,
                userId INTEGER NOT NULL,
                json TEXT NOT NULL,
                PRIMARY KEY (instanceId, userId)
            )
        """.trimIndent(),
    )

    /** Columns that must exist; value is ALTER TABLE suffix when addable without rebuild. */
    val requiredColumns: Map<String, Map<String, String>> = mapOf(
        "server_binding" to mapOf(
            "configKey" to "TEXT NOT NULL",
            "activeInstanceId" to "TEXT NOT NULL",
            "updatedAt" to "TEXT",
        ),
        "instance_registry" to mapOf(
            "instanceId" to "TEXT NOT NULL",
            "firstSeenAt" to "TEXT NOT NULL",
            "lastSeenAt" to "TEXT NOT NULL",
        ),
        "conversation" to mapOf(
            "instanceId" to "TEXT NOT NULL",
            "id" to "TEXT NOT NULL",
            "type" to "TEXT NOT NULL",
            "otherUserId" to "INTEGER",
            "displayName" to "TEXT",
            "lastMessageId" to "INTEGER",
            "lastMessagePreview" to "TEXT",
            "unreadCount" to "INTEGER NOT NULL DEFAULT 0",
            "updatedAt" to "TEXT",
            "archived" to "INTEGER NOT NULL DEFAULT 0",
        ),
        "message" to mapOf(
            "instanceId" to "TEXT NOT NULL",
            "id" to "INTEGER NOT NULL",
            "conversationId" to "TEXT NOT NULL",
            "userId" to "INTEGER NOT NULL",
            "content" to "TEXT NOT NULL",
            "timestamp" to "TEXT NOT NULL",
            "isRead" to "INTEGER NOT NULL",
            "isEdited" to "INTEGER NOT NULL",
            "replyToId" to "INTEGER",
            "clientMessageId" to "TEXT",
            "deletedFlag" to "INTEGER NOT NULL DEFAULT 0",
            "sendStatus" to "TEXT",
        ),
        "attachment" to mapOf(
            "instanceId" to "TEXT NOT NULL",
            "id" to "INTEGER NOT NULL",
            "messageId" to "INTEGER NOT NULL",
            "conversationId" to "TEXT NOT NULL",
            "remotePath" to "TEXT",
            "localPath" to "TEXT",
            "status" to "TEXT NOT NULL",
            "blurhash" to "TEXT",
            "aspectRatio" to "REAL",
            "size" to "INTEGER",
            "bytesTransferred" to "INTEGER NOT NULL DEFAULT 0",
            "clientMessageId" to "TEXT",
        ),
        "outbox" to mapOf(
            "instanceId" to "TEXT NOT NULL",
            "clientMessageId" to "TEXT NOT NULL",
            "conversationId" to "TEXT NOT NULL",
            "kind" to "TEXT NOT NULL",
            "payloadJson" to "TEXT NOT NULL",
            "retryCount" to "INTEGER NOT NULL DEFAULT 0",
            "nextAttemptAt" to "TEXT",
            "bytesUploaded" to "INTEGER NOT NULL DEFAULT 0",
        ),
        "profile_cache" to mapOf(
            "instanceId" to "TEXT NOT NULL",
            "userId" to "INTEGER NOT NULL",
            "json" to "TEXT NOT NULL",
        ),
    )

    /** Tables that require [instanceId]; missing it triggers rebuild-with-copy, not wipe. */
    val partitionedTables: Set<String> = setOf("message", "conversation", "attachment")

    val requiredIndexes: Map<String, String> = mapOf(
        "message_instance_conversation_index" to
            "CREATE INDEX IF NOT EXISTS message_instance_conversation_index ON message(instanceId, conversationId, timestamp)",
    )

    val obsoleteIndexes: Set<String> = setOf("message_conversation_index")
}
