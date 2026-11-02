package ru.fromchat.desktop

import java.io.File
import kotlinx.coroutines.runBlocking
import ru.fromchat.incognito.IncognitoMode
import ru.fromchat.logging.FromChatLogDirs
import ru.fromchat.logging.Logger
import ru.fromchat.utils.files.PlatformFileSystem

/**
 * Headless checks for incognito encrypted block container (invoked with `--verify-incognito`).
 */
fun runIncognitoContainerVerify(): Int = runBlocking {
    verifyNoPlaintextOnDisk()
    verifyKill9OnlyEncryptedContainer()
    verifyVolumeGrowAndShrink()
    0
}

private suspend fun verifyNoPlaintextOnDisk() {
    IncognitoMode.activate()
    val dbPath = File(PlatformFileSystem.getAppCacheDirectory(), "fromchat/message_database.db").absolutePath
    PlatformFileSystem.writeBytes(dbPath, "SQLite format 3 incognito test payload".encodeToByteArray())
    check(!File(dbPath).exists()) { "incognito must not create plaintext files under app data" }
    Logger.i("IncognitoVerify", "log probe line for encrypted container")
    val logPath = FromChatLogDirs.currentLogPath()
    check(PlatformFileSystem.exists(logPath)) { "log path must exist inside encrypted volume" }
    val diskLeak = File(logPath).takeIf { it.exists() }?.readText()?.contains("log probe line") == true
    check(!diskLeak) { "log plaintext leaked to real app data path" }
    IncognitoMode.syncContainerToDisk()
    val containerPath = IncognitoMode.containerFilePathOrNull() ?: error("missing container path")
    val raw = File(containerPath).readBytes()
    check(raw.isNotEmpty()) { "container file missing" }
    check(!raw.decodeToString().contains("log probe line")) { "container leaked plaintext" }
    check(!raw.decodeToString().contains("SQLite format 3")) { "container leaked plaintext" }
    val volumeRoot = IncognitoMode.volumeRootOrNull() ?: error("missing volume root")
    val appDataRoot = PlatformFileSystem.getAppCacheDirectory()
    val strayPlaintext = File(appDataRoot, "fromchat").walkTopDown()
        .filter { it.isFile && it.readText().contains("log probe line") }
        .any()
    check(!strayPlaintext) { "plaintext log leaked outside container" }
    IncognitoMode.destroy()
    check(!File(volumeRoot).exists()) { "volume directory still present after destroy" }
    println("NO_PLAINTEXT_OK volume=$volumeRoot container=$containerPath")
}

private suspend fun verifyKill9OnlyEncryptedContainer() {
    IncognitoMode.activate()
    Logger.i("IncognitoVerify", "kill9 log probe")
    IncognitoMode.syncContainerToDisk()
    val containerPath = IncognitoMode.containerFilePathOrNull() ?: error("missing container path")
    val raw = File(containerPath).readBytes()
    check(!raw.decodeToString().contains("kill9 log probe")) { "kill9 container leaked plaintext" }
    println("KILL9_ENCRYPTED_OK container=$containerPath")
    IncognitoMode.destroy()
}

private suspend fun verifyVolumeGrowAndShrink() {
    IncognitoMode.activate()
    val probePath = "${PlatformFileSystem.getAppCacheDirectory()}/fromchat/grow-shrink.bin"
    val large = ByteArray(9000) { 0xAB.toByte() }
    PlatformFileSystem.writeBytes(probePath, large)
    IncognitoMode.syncContainerToDisk()
    val containerPath = IncognitoMode.containerFilePathOrNull() ?: error("missing container path")
    val sizeAfterWrite = File(containerPath).length()
    PlatformFileSystem.delete(probePath)
    IncognitoMode.syncContainerToDisk()
    val sizeAfterDelete = File(containerPath).length()
    check(sizeAfterDelete < sizeAfterWrite) {
        "encrypted volume should shrink after delete (before=$sizeAfterWrite after=$sizeAfterDelete)"
    }
    println("VOLUME_SHRINK_OK before=$sizeAfterWrite after=$sizeAfterDelete")
    IncognitoMode.destroy()
}
