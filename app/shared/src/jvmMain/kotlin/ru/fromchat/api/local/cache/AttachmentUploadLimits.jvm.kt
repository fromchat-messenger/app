package ru.fromchat.api.local.cache

private const val JVM_MAX_BYTES = 64L * 1024L * 1024L

actual fun maxInMemoryEncryptPlaintextBytes(): Long = JVM_MAX_BYTES
