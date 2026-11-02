package ru.fromchat.incognito

import java.io.File
import java.security.SecureRandom
import kotlin.random.Random

internal actual suspend fun provisionIncognitoVolumeRoot(): String {
    val base = pickRandomDesktopVolumeBase()
    val parent = if (Random.nextBoolean()) {
        base
    } else {
        File(base, randomHiddenDirectoryName()).also { it.mkdirs() }
    }
    val volume = File(parent, randomHiddenDirectoryName()).also { it.mkdirs() }
    File(volume, "cache").mkdirs()
    return volume.absolutePath
}

internal actual fun destroyIncognitoVolumeRoot(rootPath: String) {
    File(rootPath).deleteRecursively()
}

internal actual fun registerIncognitoLifecycleHooks() {
    Runtime.getRuntime().addShutdownHook(
        Thread(
            {
                IncognitoMode.destroy()
            },
            "fromchat-incognito-shutdown",
        ),
    )
}

private fun pickRandomDesktopVolumeBase(): File {
    val home = File(System.getProperty("user.home") ?: ".")
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val candidates = buildList {
        if (os.contains("win")) {
            System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            System.getenv("USERPROFILE")?.takeIf { it.isNotBlank() }?.let { profile ->
                add(File(profile, "Downloads"))
                add(File(profile, "Documents"))
                add(File(profile, "Music"))
                add(File(profile, "Pictures"))
            }
        } else if (os.contains("mac")) {
            add(File(home, "Downloads"))
            add(File(home, "Documents"))
            add(File(home, "Music"))
            add(File(home, "Pictures"))
            add(File(home, "Library/Application Support"))
        } else {
            add(File(home, "Downloads"))
            add(File(home, "Documents"))
            add(File(home, "Music"))
            add(File(home, "Pictures"))
            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
                ?: add(File(home, ".local/share"))
        }
    }.filter { it.isDirectory || runCatching { it.mkdirs() }.getOrDefault(false) }

    return candidates.randomOrNull() ?: home
}

private fun randomHiddenDirectoryName(): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    val length = 8 + SecureRandom().nextInt(5)
    return buildString(length) {
        repeat(length) {
            append(alphabet[SecureRandom().nextInt(alphabet.length)])
        }
    }
}
