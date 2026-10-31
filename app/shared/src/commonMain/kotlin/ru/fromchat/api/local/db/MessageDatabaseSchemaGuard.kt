package ru.fromchat.api.local.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.runBlocking
import ru.fromchat.config.Settings
import ru.fromchat.api.instance.isValidInstanceUuid

/**
 * Aligns on-disk SQLite with [MessageDatabaseExpectedSchema] by structural diff only
 * (no user_version / schema version buckets). Full wipe is last resort.
 */
internal const val UNBOUND_MIGRATION_INSTANCE_ID = "00000000-0000-4000-8000-000000000001"

internal fun ensureMessageDatabaseSchema(driver: SqlDriver) {
  runCatching { migrateMessageDatabaseSchema(driver) }
    .onFailure { recreateMessageDatabaseSchema(driver) }
}

/** Reassign rows stored under [UNBOUND_MIGRATION_INSTANCE_ID] when a real instance id is known. */
internal fun rebindUnboundMigrationInstance(driver: SqlDriver, targetInstanceId: String) {
  val target = targetInstanceId.trim()
  if (target.isEmpty() || !isValidInstanceUuid(target)) return
  if (target == UNBOUND_MIGRATION_INSTANCE_ID) return
  if (!tableExists(driver, "message")) return
  val tablesWithInstance = listOf(
    "message",
    "conversation",
    "attachment",
    "outbox",
    "profile_cache",
  )
  inTransaction(driver) {
    for (table in tablesWithInstance) {
      if (!tableExists(driver, table)) continue
      if (!columnExists(driver, table, "instanceId")) continue
      driver.execute(
        identifier = null,
        sql = "UPDATE $table SET instanceId = ? WHERE instanceId = ?",
        parameters = 2,
        binders = {
          bindString(0, target)
          bindString(1, UNBOUND_MIGRATION_INSTANCE_ID)
        },
      )
    }
  }
}

private data class ColumnAdd(val table: String, val column: String, val definition: String)

private enum class PartitionedTableRebuild {
  Message,
  Conversation,
  Attachment,
}

private data class SchemaDiff(
  val createTables: List<String> = emptyList(),
  val partitionRebuilds: List<PartitionedTableRebuild> = emptyList(),
  val addColumns: List<ColumnAdd> = emptyList(),
  val ensureIndexes: List<Pair<String, String>> = emptyList(),
  val dropIndexes: Set<String> = emptySet(),
  val unrecoverableReason: String? = null,
)

private fun migrateMessageDatabaseSchema(driver: SqlDriver) {
  val diff = computeSchemaDiff(driver)
  diff.unrecoverableReason?.let { error(it) }
  if (diff.isNoOp()) {
    normalizeLegacyConversationIds(driver)
    return
  }
  applySchemaDiff(driver, diff)
  normalizeLegacyConversationIds(driver)
}

private fun SchemaDiff.isNoOp(): Boolean =
  createTables.isEmpty() &&
    partitionRebuilds.isEmpty() &&
    addColumns.isEmpty() &&
    ensureIndexes.isEmpty() &&
    dropIndexes.isEmpty()

private fun computeSchemaDiff(driver: SqlDriver): SchemaDiff {
  val tables = listTables(driver)
  if (tables.isEmpty()) {
    return SchemaDiff(
      createTables = MessageDatabaseExpectedSchema.managedTables.toList(),
      ensureIndexes = MessageDatabaseExpectedSchema.requiredIndexes.map { it.key to it.value },
    )
  }

  val createTables = mutableListOf<String>()
  val partitionRebuilds = mutableListOf<PartitionedTableRebuild>()
  val addColumns = mutableListOf<ColumnAdd>()
  var unrecoverable: String? = null

  fun fail(reason: String) {
    if (unrecoverable == null) unrecoverable = reason
  }

  for (tableName in MessageDatabaseExpectedSchema.managedTables) {
    if (unrecoverable != null) break
    val required = MessageDatabaseExpectedSchema.requiredColumns[tableName] ?: continue
    if (!tables.contains(tableName)) {
      createTables.add(tableName)
      continue
    }

    val actual = columnNames(driver, tableName)
    if (tableName in MessageDatabaseExpectedSchema.partitionedTables && !actual.contains("instanceId")) {
      if (!legacyPartitionCorePresent(tableName, actual)) {
        fail("$tableName exists but lacks instanceId and recognizable legacy columns")
        continue
      }
      partitionRebuilds.add(tableName.toPartitionRebuild())
      continue
    }

    if (!partitionCorePresent(tableName, actual)) {
      fail("$tableName is missing required columns: ${required.keys - actual}")
      continue
    }

    for ((column, definition) in required) {
      if (actual.contains(column)) continue
      if (column == "instanceId") continue
      addColumns.add(ColumnAdd(tableName, column, definition))
    }
  }

  if (unrecoverable != null) {
    return SchemaDiff(unrecoverableReason = unrecoverable)
  }

  val ensureIndexes = MessageDatabaseExpectedSchema.requiredIndexes
    .filterNot { (name, _) -> indexExists(driver, name) }
    .map { it.key to it.value }

  val dropIndexes = MessageDatabaseExpectedSchema.obsoleteIndexes
    .filter { indexExists(driver, it) }
    .toSet()

  return SchemaDiff(
    createTables = createTables,
    partitionRebuilds = partitionRebuilds.distinct(),
    addColumns = addColumns,
    ensureIndexes = ensureIndexes,
    dropIndexes = dropIndexes,
  )
}

private fun String.toPartitionRebuild(): PartitionedTableRebuild =
  when (this) {
    "message" -> PartitionedTableRebuild.Message
    "conversation" -> PartitionedTableRebuild.Conversation
    "attachment" -> PartitionedTableRebuild.Attachment
    else -> error("Not a partitioned table: $this")
  }

private fun legacyPartitionCorePresent(table: String, actual: Set<String>): Boolean =
  when (table) {
    "message" ->
      actual.containsAll(
        setOf(
          "id",
          "conversationId",
          "userId",
          "content",
          "timestamp",
          "isRead",
          "isEdited",
          "deletedFlag",
        ),
      )
    "conversation" -> actual.containsAll(setOf("id", "type"))
    "attachment" -> actual.containsAll(setOf("id", "messageId", "conversationId", "status"))
    else -> false
  }

private fun partitionCorePresent(table: String, actual: Set<String>): Boolean {
  val required = MessageDatabaseExpectedSchema.requiredColumns[table] ?: return true
  val core =
    when (table) {
      "message" ->
        setOf(
          "instanceId",
          "id",
          "conversationId",
          "userId",
          "content",
          "timestamp",
          "isRead",
          "isEdited",
          "deletedFlag",
        )
      "conversation" -> setOf("instanceId", "id", "type")
      "attachment" -> setOf("instanceId", "id", "messageId", "conversationId", "status")
      "server_binding" -> setOf("configKey", "activeInstanceId")
      "instance_registry" -> setOf("instanceId", "firstSeenAt", "lastSeenAt")
      "outbox" ->
        setOf("instanceId", "clientMessageId", "conversationId", "kind", "payloadJson", "retryCount")
      "profile_cache" -> setOf("instanceId", "userId", "json")
      else -> required.keys
    }
  return core.all { actual.contains(it) }
}

private fun applySchemaDiff(driver: SqlDriver, diff: SchemaDiff) {
  val instanceId = migrationInstanceId()
  inTransaction(driver) {
    for (rebuild in diff.partitionRebuilds) {
      when (rebuild) {
        PartitionedTableRebuild.Message -> rebuildLegacyMessageTable(driver, instanceId)
        PartitionedTableRebuild.Conversation -> rebuildLegacyConversationTable(driver, instanceId)
        PartitionedTableRebuild.Attachment -> rebuildLegacyAttachmentTable(driver, instanceId)
      }
    }
    for (table in diff.createTables) {
      val sql = MessageDatabaseExpectedSchema.tableCreateSql[table]
        ?: error("Missing CREATE SQL for $table")
      driver.execute(identifier = null, sql = sql, parameters = 0)
    }
    for (add in diff.addColumns) {
      if (!tableExists(driver, add.table)) continue
      if (columnExists(driver, add.table, add.column)) continue
      driver.execute(
        identifier = null,
        sql = "ALTER TABLE ${add.table} ADD COLUMN ${add.column} ${add.definition}",
        parameters = 0,
      )
    }
    for (indexName in diff.dropIndexes) {
      driver.execute(identifier = null, sql = "DROP INDEX IF EXISTS $indexName", parameters = 0)
    }
    for ((_, createSql) in diff.ensureIndexes) {
      driver.execute(identifier = null, sql = createSql, parameters = 0)
    }
  }
}

private fun applyExpectedSchema(driver: SqlDriver) {
  for (table in MessageDatabaseExpectedSchema.managedTables) {
    val sql = MessageDatabaseExpectedSchema.tableCreateSql[table] ?: continue
    driver.execute(identifier = null, sql = sql, parameters = 0)
  }
  for ((_, createSql) in MessageDatabaseExpectedSchema.requiredIndexes) {
    driver.execute(identifier = null, sql = createSql, parameters = 0)
  }
}

private fun normalizeLegacyConversationIds(driver: SqlDriver) {
  if (!tableExists(driver, "message")) return
  runCatching {
    driver.execute(
      identifier = null,
      sql = "UPDATE message SET conversationId = '-1' WHERE conversationId = 'public'",
      parameters = 0,
    )
  }
  if (!tableExists(driver, "conversation")) return
  runCatching {
    driver.execute(
      identifier = null,
      sql = "UPDATE conversation SET id = '-1' WHERE id = 'public'",
      parameters = 0,
    )
  }
}

private fun migrationInstanceId(): String {
  // Do not read via Settings.lastKnownServerInstanceId here — its getter nests runBlocking and
  // deadlocks when this runs on the single-thread SQLite executor while the UI waits on .get().
  val known = runBlocking { Settings.readLastKnownServerInstanceIdRaw().trim() }
  if (known.isNotEmpty() && isValidInstanceUuid(known)) return known
  return UNBOUND_MIGRATION_INSTANCE_ID
}

private fun rebuildLegacyMessageTable(driver: SqlDriver, instanceId: String) {
  driver.execute(identifier = null, sql = "DROP INDEX IF EXISTS message_conversation_index", parameters = 0)
  driver.execute(
    identifier = null,
    sql = """
    CREATE TABLE message__migrate (
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
    parameters = 0,
  )
  driver.execute(
    identifier = null,
    sql = """
    INSERT INTO message__migrate(
        instanceId, id, conversationId, userId, content, timestamp,
        isRead, isEdited, replyToId, clientMessageId, deletedFlag, sendStatus
    )
    SELECT
        ?, id,
        CASE WHEN conversationId = 'public' THEN '-1' ELSE conversationId END,
        userId, content, timestamp, isRead, isEdited, replyToId, clientMessageId, deletedFlag,
        CASE WHEN id < 0 THEN 'pending' ELSE 'sent' END
    FROM message
    """.trimIndent(),
    parameters = 1,
    binders = { bindString(0, instanceId) },
  )
  driver.execute(identifier = null, sql = "DROP TABLE message", parameters = 0)
  driver.execute(identifier = null, sql = "ALTER TABLE message__migrate RENAME TO message", parameters = 0)
  driver.execute(
    identifier = null,
    sql = MessageDatabaseExpectedSchema.requiredIndexes.getValue("message_instance_conversation_index"),
    parameters = 0,
  )
}

private fun rebuildLegacyConversationTable(driver: SqlDriver, instanceId: String) {
  driver.execute(
    identifier = null,
    sql = """
    CREATE TABLE conversation__migrate (
        instanceId TEXT NOT NULL,
        id TEXT NOT NULL,
        type TEXT NOT NULL,
        otherUserId INTEGER,
        displayName TEXT,
        lastMessageId INTEGER,
        lastMessagePreview TEXT,
        unreadCount INTEGER NOT NULL DEFAULT 0,
        updatedAt TEXT,
        PRIMARY KEY (instanceId, id)
    )
    """.trimIndent(),
    parameters = 0,
  )
  driver.execute(
    identifier = null,
    sql = """
    INSERT INTO conversation__migrate(
        instanceId, id, type, otherUserId, displayName,
        lastMessageId, lastMessagePreview, unreadCount, updatedAt
    )
    SELECT
        ?,
        CASE WHEN id = 'public' THEN '-1' ELSE id END,
        type, otherUserId, displayName,
        lastMessageId, lastMessagePreview, unreadCount, updatedAt
    FROM conversation
    """.trimIndent(),
    parameters = 1,
    binders = { bindString(0, instanceId) },
  )
  driver.execute(identifier = null, sql = "DROP TABLE conversation", parameters = 0)
  driver.execute(identifier = null, sql = "ALTER TABLE conversation__migrate RENAME TO conversation", parameters = 0)
}

private fun rebuildLegacyAttachmentTable(driver: SqlDriver, instanceId: String) {
  driver.execute(
    identifier = null,
    sql = """
    CREATE TABLE attachment__migrate (
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
    parameters = 0,
  )
  driver.execute(
    identifier = null,
    sql = """
    INSERT INTO attachment__migrate(
        instanceId, id, messageId, conversationId, remotePath, localPath,
        status, blurhash, aspectRatio, size, bytesTransferred, clientMessageId
    )
    SELECT
        ?, id, messageId,
        CASE WHEN conversationId = 'public' THEN '-1' ELSE conversationId END,
        remotePath, localPath, status, blurhash, aspectRatio, size, 0, NULL
    FROM attachment
    """.trimIndent(),
    parameters = 1,
    binders = { bindString(0, instanceId) },
  )
  driver.execute(identifier = null, sql = "DROP TABLE attachment", parameters = 0)
  driver.execute(identifier = null, sql = "ALTER TABLE attachment__migrate RENAME TO attachment", parameters = 0)
}

private fun recreateMessageDatabaseSchema(driver: SqlDriver) {
  val drops = listOf(
    "DROP INDEX IF EXISTS message_instance_conversation_index",
    "DROP INDEX IF EXISTS message_conversation_index",
    "DROP TABLE IF EXISTS message",
    "DROP TABLE IF EXISTS message__migrate",
    "DROP TABLE IF EXISTS attachment",
    "DROP TABLE IF EXISTS attachment__migrate",
    "DROP TABLE IF EXISTS outbox",
    "DROP TABLE IF EXISTS profile_cache",
    "DROP TABLE IF EXISTS public_chat_profile",
    "DROP TABLE IF EXISTS conversation",
    "DROP TABLE IF EXISTS conversation__migrate",
    "DROP TABLE IF EXISTS server_binding",
    "DROP TABLE IF EXISTS instance_registry",
  )
  for (sql in drops) {
    driver.execute(identifier = null, sql = sql, parameters = 0)
  }
  applyExpectedSchema(driver)
}

private fun inTransaction(driver: SqlDriver, block: () -> Unit) {
  driver.execute(identifier = null, sql = "BEGIN IMMEDIATE", parameters = 0)
  try {
    block()
    driver.execute(identifier = null, sql = "COMMIT", parameters = 0)
  } catch (e: Exception) {
    runCatching { driver.execute(identifier = null, sql = "ROLLBACK", parameters = 0) }
    throw e
  }
}

private fun listTables(driver: SqlDriver): Set<String> {
  val names = mutableSetOf<String>()
  driver.executeQuery(
    identifier = null,
    sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
    mapper = { cursor ->
      while (cursor.next().value) {
        names.add(cursor.getString(0)!!)
      }
      QueryResult.Value(Unit)
    },
    parameters = 0,
  )
  return names
}

private fun columnNames(driver: SqlDriver, table: String): Set<String> {
  val names = mutableSetOf<String>()
  driver.executeQuery(
    identifier = null,
    sql = "PRAGMA table_info($table)",
    mapper = { cursor ->
      while (cursor.next().value) {
        names.add(cursor.getString(1)!!)
      }
      QueryResult.Value(Unit)
    },
    parameters = 0,
  )
  return names
}

private fun tableExists(driver: SqlDriver, table: String): Boolean =
  listTables(driver).contains(table)

private fun columnExists(driver: SqlDriver, table: String, column: String): Boolean =
  columnNames(driver, table).contains(column)

private fun indexExists(driver: SqlDriver, indexName: String): Boolean =
  driver.executeQuery(
    identifier = null,
    sql = "SELECT 1 FROM sqlite_master WHERE type='index' AND name=? LIMIT 1",
    mapper = { cursor -> QueryResult.Value(cursor.next().value) },
    parameters = 1,
    binders = { bindString(0, indexName) },
  ).value
