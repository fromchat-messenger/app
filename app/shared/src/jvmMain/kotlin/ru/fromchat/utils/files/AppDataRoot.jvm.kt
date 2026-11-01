package ru.fromchat.utils.files

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
/** True when this JVM should use portable layout (cfg, marker, or env). */
fun isPortableDesktop(): Boolean =
    System.getProperty("fromchat.portable")?.equals("true", ignoreCase = true) == true

/** Detect jpackage portable folder layout before single-instance bind. */
fun configurePortableLayoutFromInstallDir() {
    AppDataRoot.ensurePortablePropertiesFromLayout()
    AppDataRoot.ensurePortableInstanceIdFile()
}

/** Stable id stored in the portable folder (moves with the directory; used for Windows taskbar identity). */
fun ensurePortableLayoutInstanceId(): String? = AppDataRoot.ensurePortableInstanceIdFile()

/** Env vars set by the Windows portable launcher (jpackage `-J` flags are unreliable). */
fun applyFromChatLauncherEnvironment() {
    when (System.getenv("FROMCHAT_LAUNCHER")?.trim()?.lowercase()) {
        "portable" -> System.setProperty("fromchat.portable", "true")
    }
    System.getenv("FROMCHAT_EXE_DIR")?.trim()?.takeIf { it.isNotEmpty() }?.let { dir ->
        System.setProperty("fromchat.exe.dir", dir)
    }
    System.getenv("FROMCHAT_APP_DATA_NAME")?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
        if (System.getProperty("fromchat.app.data.name").isNullOrBlank()) {
            System.setProperty("fromchat.app.data.name", name)
        }
    }
}

/** One IPC scope per on-disk data root (installed vs portable vs separate portable folders). */
fun desktopSingleInstanceScopeKey(): String = AppDataRoot.singleInstanceScopeKey()

internal object AppDataRoot {
    private const val LEGACY_RELATIVE = ".fromchat/cache"
    private const val PORTABLE_DIR_NAME = "fromchat-data"
    private const val PORTABLE_BETA_DIR_NAME = "fromchat-data-beta"
    private const val APP_DIR_NAME = "FromChat"
    private const val APP_BETA_DIR_NAME = "FromChat Beta"
    private const val PORTABLE_INSTANCE_FILE = ".fromchat-portable-instance"

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

    fun singleInstanceScopeKey(): String =
        resolveUncached().absoluteFile.toPath().normalize().toString()

    fun ensurePortablePropertiesFromLayout() {
        if (isPortable()) {
            ensurePortableExeDirProperty()
            invalidateCachedRoot()
            return
        }
        val markerRoot = readPortableMarkerRoot()
        if (markerRoot != null) {
            applyPortableRoot(markerRoot)
            return
        }
        for (root in portableLayoutCandidateDirs()) {
            if (!looksLikePortableRoot(root)) continue
            applyPortableRoot(root)
            return
        }
    }

    private fun ensurePortableExeDirProperty() {
        readPortableMarkerRoot()?.let {
            System.setProperty("fromchat.exe.dir", it.absolutePath)
            return
        }
        if (!System.getProperty("fromchat.exe.dir").isNullOrBlank()) return
        val fromRuntime = executableDirectoryFromRuntimeOnly()
        System.setProperty("fromchat.exe.dir", fromRuntime.absolutePath)
    }

    private fun executableDirectoryFromRuntimeOnly(): File {
        val javaHome = System.getProperty("java.home")
        if (!javaHome.isNullOrBlank()) {
            val runtime = File(javaHome).absoluteFile
            if (runtime.name.equals("runtime", ignoreCase = true)) {
                runtime.parentFile?.let { return it }
            }
        }
        return File(System.getProperty("user.dir") ?: ".").absoluteFile
    }

    private fun applyPortableRoot(root: File) {
        System.setProperty("fromchat.portable", "true")
        System.setProperty("fromchat.exe.dir", root.absolutePath)
        invalidateCachedRoot()
    }

    private fun invalidateCachedRoot() {
        cached = null
    }

    fun portableLayoutRoot(): File? {
        readPortableMarkerRoot()?.let { return it }
        if (!isPortable()) {
            for (root in portableLayoutCandidateDirs()) {
                if (looksLikePortableRoot(root)) return root
            }
            return null
        }
        System.getProperty("fromchat.exe.dir")?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
            File(path).takeIf { it.isDirectory }?.let { dir ->
                if (File(dir, ".fromchat-portable-id").isFile || looksLikePortableRoot(dir)) return dir
                dir.parentFile?.takeIf { parent ->
                    File(parent, ".fromchat-portable-id").isFile
                }?.let { return it }
                return dir
            }
        }
        return null
    }

    /** Creates [.fromchat-portable-instance] on first run if missing (legacy portable trees). */
    fun ensurePortableInstanceIdFile(): String? {
        val root = portableLayoutRoot() ?: return null
        val file = File(root, PORTABLE_INSTANCE_FILE)
        readPortableInstanceId(file)?.let { return it }
        val id = java.util.UUID.randomUUID().toString().replace("-", "")
        file.writeText("$id\n")
        return id
    }

    private fun readPortableInstanceId(file: File): String? {
        if (!file.isFile) return null
        val id = file.readText().trim()
        return id.takeIf { it.length == 32 && id.all { ch -> ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F' } }
            ?.lowercase()
    }

    /** `.fromchat-portable-id` next to jpackage `runtime/` (works even when cfg JavaOptions were wrong). */
    private fun readPortableMarkerRoot(): File? {
        val fromRuntime = executableDirectoryFromRuntimeOnly()
        if (File(fromRuntime, ".fromchat-portable-id").isFile) return fromRuntime
        fromRuntime.parentFile?.let { parent ->
            if (File(parent, ".fromchat-portable-id").isFile) return parent
        }
        val fromExeDir = System.getProperty("fromchat.exe.dir")?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it) }
        if (fromExeDir != null) {
            if (File(fromExeDir, ".fromchat-portable-id").isFile) return fromExeDir
            fromExeDir.parentFile?.let { parent ->
                if (File(parent, ".fromchat-portable-id").isFile) return parent
            }
        }
        return null
    }

    private fun portableLayoutCandidateDirs(): List<File> {
        val exeDir = executableDirectory()
        val roots = linkedSetOf(exeDir)
        exeDir.parentFile?.let { roots.add(it) }
        return roots.toList()
    }

    private fun looksLikePortableRoot(root: File): Boolean {
        if (File(root, ".fromchat-portable-id").isFile) return true
        if (File(root, PORTABLE_DIR_NAME).isDirectory) return true
        if (File(root, PORTABLE_BETA_DIR_NAME).isDirectory) return true
        return false
    }

    private fun resolveUncached(): File {
        devDataRoot()?.let { return it }
        if (isPortable()) {
            val layoutRoot = portableLayoutRoot() ?: executableDirectory()
            return File(layoutRoot, portableDirName())
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
