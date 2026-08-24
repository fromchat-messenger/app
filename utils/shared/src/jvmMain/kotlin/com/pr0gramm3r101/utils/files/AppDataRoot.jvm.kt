package com.pr0gramm3r101.utils.files

import java.io.File

/**
 * Resolves the FromChat desktop data/cache root on the JVM.
 *
 * - Portable (`-Dfromchat.portable=true`): `<exeDir>/fromchat-data`
 * - Windows installed: `%LOCALAPPDATA%\FromChat`
 * - macOS: `~/Library/Application Support/FromChat`
 * - Linux: `${XDG_DATA_HOME:-~/.local/share}/FromChat`
 *
 * Migrates once from the legacy `~/.fromchat/cache` directory when present.
 */
internal object AppDataRoot {
    private const val LEGACY_RELATIVE = ".fromchat/cache"
    private const val PORTABLE_DIR_NAME = "fromchat-data"
    private const val APP_DIR_NAME = "FromChat"

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
        if (isPortable()) {
            return File(executableDirectory(), PORTABLE_DIR_NAME)
        }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            os.contains("win") -> windowsInstalledRoot()
            os.contains("mac") -> macInstalledRoot()
            else -> linuxInstalledRoot()
        }
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

    private fun windowsInstalledRoot(): File {
        val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        return if (localAppData != null) {
            File(localAppData, APP_DIR_NAME)
        } else {
            File(requireHome(), APP_DIR_NAME)
        }
    }

    private fun macInstalledRoot(): File =
        File(requireHome(), "Library/Application Support/$APP_DIR_NAME")

    private fun linuxInstalledRoot(): File {
        val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
        return if (xdg != null) {
            File(xdg, APP_DIR_NAME)
        } else {
            File(requireHome(), ".local/share/$APP_DIR_NAME")
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
