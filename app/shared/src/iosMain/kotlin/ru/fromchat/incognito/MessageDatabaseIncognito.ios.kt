package ru.fromchat.incognito

import app.cash.sqldelight.db.SqlDriver

actual fun exportMessageDatabaseBytes(driver: SqlDriver): ByteArray? = null

actual fun importMessageDatabaseBytes(driver: SqlDriver, bytes: ByteArray) = Unit
