package ru.fromchat.incognito

/**
 * Routes app storage paths into the incognito work tree (same layout as normal app data).
 * When incognito is off, paths pass through unchanged.
 */
object IncognitoStorageRouter {
    fun resolveAppDataPath(normalAppDataPath: String): String {
        if (!IncognitoMode.active) return normalAppDataPath
        val workRoot = IncognitoContainer.workRootPath ?: return normalAppDataPath
        val defaultRoot = defaultAppDataRootPath()
        val normalizedDefault = defaultRoot.replace('\\', '/').trimEnd('/')
        val normalizedPath = normalAppDataPath.replace('\\', '/')
        val suffix = when {
            normalizedPath == normalizedDefault -> ""
            normalizedPath.startsWith("$normalizedDefault/") ->
                normalizedPath.removePrefix("$normalizedDefault/")
            else -> return normalAppDataPath
        }
        val workNorm = workRoot.replace('\\', '/').trimEnd('/')
        return if (suffix.isEmpty()) workNorm else "$workNorm/$suffix"
    }
}

internal expect fun defaultAppDataRootPath(): String
