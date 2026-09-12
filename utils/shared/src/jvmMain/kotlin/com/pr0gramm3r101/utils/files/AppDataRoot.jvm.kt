package com.pr0gramm3r101.utils.files

import java.io.File

/**
 * Resolves the FromChat desktop data/cache root on the JVM.
 *
 * - Custom data root (`-Dfromchat.dev.data.dir=…`): use only when set explicitly (e.g. IDE run config)
 * - Portable (`-Dfromchat.portable=true`): `<exeDir>/fromchat-data` or `fromchat-data-beta`
 * - Windows: `%LOCALAPPDATA%\FromChat` or `%LOCALAPPDATA%\FromChat Beta`
 * - macOS: `~/Library/Application Support/FromChat` or `…/FromChat Beta`
 * - Linux: `~/.local/share/FromChat` or `…/FromChat Beta`
 *
 * Variant is selected via `-Dfromchat.app.data.name` (`FromChatBeta` → `FromChat Beta`; default `FromChat`).
 *
 * Migrates once from the legacy `~/.fromchat/cache` directory when present.
 */
internal object AppDataRoot {
    private const val LEGACY_RELATIVE = ".fromchat/cache"
    private const val PORTABLE_DIR_NAME = "fromchat-data"
    private const val PORTABLE_BETA_DIR_NAME = "fromchat-data-beta"
    private const val APP_DIR_NAME = "FromChat"
    private const val APP_BETA_DIR_NAME = "FromChat Beta"

    @Volatile
    private var cached: File? = null

    fun resolve(): File {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val root = resolveUncached().also { it.mkdirs() }
            migrateLegacyIfNeeded(root)
            cached = root
            return root
        }
    }

    private fun resolveUncached(): File {
        devDataRoot()?.let { return it }
        if (isPortable()) {
            return File(executableDirectory(), portableDirName())
        }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            os.contains("win") -> windowsInstalledRoot()
            os.contains("mac") -> macInstalledRoot()
            else -> linuxInstalledRoot()
        }
    }

    private fun devDataRoot(): File? {
        val explicit = System.getProperty("fromchat.dev.data.dir")?.trim()?.takeIf { it.isNotEmpty() }
        return explicit?.let { File(it).absoluteFile }
    }

    private fun isPortable(): Boolean =
        System.getProperty("fromchat.portable")
            ?.equals("true", ignoreCase = true) == true

    private fun executableDirectory(): File {
        System.getProperty("fromchat.exe.dir")?.takeIf { it.isNotBlank() }?.let {
            return File(it).absoluteFile
        }
        // jpackage layout: <app>/runtime → parent is the install/portable folder
        val javaHome = System.getProperty("java.home")
        if (!javaHome.isNullOrBlank()) {
            val runtime = File(javaHome).absoluteFile
            if (runtime.name.equals("runtime", ignoreCase = true)) {
                val parent = runtime.parentFile
                if (parent != null) return parent
            }
        }
        return File(System.getProperty("user.dir") ?: ".").absoluteFile
    }

    private fun installedAppDirName(): String {
        val raw = System.getProperty("fromchat.app.data.name")?.trim()?.takeIf { it.isNotEmpty() }
        return when (raw) {
            "FromChatBeta", APP_BETA_DIR_NAME -> APP_BETA_DIR_NAME
            else -> raw ?: APP_DIR_NAME
        }
    }

    private fun portableDirName(): String =
        if (installedAppDirName() == APP_BETA_DIR_NAME) PORTABLE_BETA_DIR_NAME else PORTABLE_DIR_NAME

    private fun windowsInstalledRoot(): File {
        val dirName = installedAppDirName()
        val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        return if (localAppData != null) {
            File(localAppData, dirName)
        } else {
            File(requireHome(), dirName)
        }
    }

    private fun macInstalledRoot(): File =
        File(requireHome(), "Library/Application Support/${installedAppDirName()}")

    private fun linuxInstalledRoot(): File {
        val dirName = installedAppDirName()
        val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
        return if (xdg != null) {
            File(xdg, dirName)
        } else {
            File(requireHome(), ".local/share/$dirName")
        }
    }

    private fun requireHome(): File {
        val home = System.getProperty("user.home")
        return if (!home.isNullOrBlank()) {
            File(home)
        } else {
            File(System.getProperty("java.io.tmpdir") ?: ".", "fromchat-home")
        }
    }

    private fun legacyCacheDir(): File? {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() } ?: return null
        return File(home, LEGACY_RELATIVE)
    }

    private fun migrateLegacyIfNeeded(target: File) {
        if (isPortable()) return
        val legacy = legacyCacheDir() ?: return
        if (!legacy.isDirectory) return
        val marker = File(target, ".migrated-from-legacy-cache")
        if (marker.isFile) return
        val targetEmpty = target.list().isNullOrEmpty()
        if (!targetEmpty) {
            marker.writeText("skipped-non-empty\n")
            return
        }
        runCatching {
            legacy.copyRecursively(target, overwrite = false)
            marker.writeText("ok\n")
            legacy.deleteRecursively()
        }
    }
}
