package ru.fromchat.desktop

import java.io.File
import kotlinx.coroutines.runBlocking
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.incognito.IncognitoVault
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
    File(cacheDir, "message_database.db").writeBytes(
        "SQLite format 3 incognito test payload".encodeToByteArray(),
    )
    IncognitoMode.syncContainerToDisk()
    val containerPath = IncognitoMode.containerFilePathOrNull()
        ?: error("missing container path")
    val sealed = File(containerPath).readBytes()
    check(IncognitoVault.isSealedPayload(sealed)) { "container file is not sealed" }
    check(!sealed.decodeToString().contains("SQLite format 3")) { "plaintext in sealed container" }
    val volumeRoot = IncognitoMode.volumeRootOrNull() ?: error("missing volume root")
    IncognitoMode.destroy()
    check(!File(volumeRoot).exists()) { "volume directory still present after destroy" }
    println("GRACEFUL_WIPE_OK volume=$volumeRoot")
}

/** After sync, on-disk state matches what SIGKILL would leave (encrypted single container file). */
private suspend fun verifySyncedContainerIsEncryptedAtRest() {
    IncognitoMode.activate()
    val cacheDir = File(PlatformFileSystem.getAppCacheDirectory(), "fromchat").apply { mkdirs() }
    File(cacheDir, "message_database.db").writeBytes(
        "SQLite format 3 kill9 simulation".encodeToByteArray(),
    )
    IncognitoMode.syncContainerToDisk()
    val containerPath = IncognitoMode.containerFilePathOrNull()
        ?: error("missing container path")
    val volumeRoot = IncognitoMode.volumeRootOrNull() ?: error("missing volume root")
    val sealed = File(containerPath).readBytes()
    check(IncognitoVault.isSealedPayload(sealed)) { "kill9 container not sealed" }
    check(!sealed.decodeToString().contains("SQLite format 3")) { "kill9 container leaked plaintext" }
    println("KILL9_ENCRYPTED_OK container=$containerPath volume=$volumeRoot")
    IncognitoMode.destroy()
}
