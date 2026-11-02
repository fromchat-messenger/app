package ru.fromchat.desktop

import java.io.File
import kotlinx.coroutines.runBlocking
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.incognito.IncognitoVault
import ru.fromchat.logging.FromChatLogDirs
import ru.fromchat.logging.Logger
import ru.fromchat.utils.files.PlatformFileSystem

/**
 * Headless checks for incognito encrypted container (invoked with `--verify-incognito`).
 */
fun runIncognitoContainerVerify(): Int = runBlocking {
    verifyGracefulWipe()
    verifySyncedContainerIsEncryptedAtRest()
    0
}

private suspend fun verifyGracefulWipe() {
    IncognitoMode.activate()
    val cacheDir = File(PlatformFileSystem.getAppCacheDirectory(), "fromchat").apply { mkdirs() }
    val dbPath = File(cacheDir, "message_database.db").absolutePath
    PlatformFileSystem.writeBytes(dbPath, "SQLite format 3 incognito test payload".encodeToByteArray())
    check(File(dbPath).exists()) { "incognito work tree must accept normal file writes" }
    Logger.i("IncognitoVerify", "log probe line for sealed container")
    val logPath = FromChatLogDirs.currentLogPath()
    check(File(logPath).exists()) { "logs must use the same path layout inside the volume work tree" }
    check(File(logPath).readText().contains("log probe line")) { "log content missing from work tree" }
    IncognitoMode.syncContainerToDisk()
    val containerPath = IncognitoMode.containerFilePathOrNull()
        ?: error("missing container path")
    val sealed = File(containerPath).readBytes()
    check(IncognitoVault.isSealedPayload(sealed)) { "container file is not sealed" }
    check(!sealed.decodeToString().contains("SQLite format 3")) { "plaintext in sealed container" }
    check(!sealed.decodeToString().contains("log probe line")) { "log plaintext in sealed container" }
    val volumeRoot = IncognitoMode.volumeRootOrNull() ?: error("missing volume root")
    IncognitoMode.destroy()
    check(!File(volumeRoot).exists()) { "volume directory still present after destroy" }
    println("GRACEFUL_WIPE_OK volume=$volumeRoot")
}

/** After sync, on-disk state matches what SIGKILL would leave (encrypted single container file). */
private suspend fun verifySyncedContainerIsEncryptedAtRest() {
    IncognitoMode.activate()
    Logger.i("IncognitoVerify", "kill9 log probe")
    IncognitoMode.syncContainerToDisk()
    val containerPath = IncognitoMode.containerFilePathOrNull()
        ?: error("missing container path")
    val volumeRoot = IncognitoMode.volumeRootOrNull() ?: error("missing volume root")
    val sealed = File(containerPath).readBytes()
    check(IncognitoVault.isSealedPayload(sealed)) { "kill9 container not sealed" }
    check(!sealed.decodeToString().contains("log probe")) { "kill9 container leaked plaintext" }
    println("KILL9_ENCRYPTED_OK container=$containerPath volume=$volumeRoot")
    IncognitoMode.destroy()
}
