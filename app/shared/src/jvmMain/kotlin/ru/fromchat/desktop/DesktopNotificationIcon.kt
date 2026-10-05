package ru.fromchat.desktop

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

/** Writes a window/tray icon PNG for native notification APIs (toast, libnotify). */
internal object DesktopNotificationIcon {
    @Volatile
    private var cachedPath: String? = null

    fun materialize(): String? {
        cachedPath?.let { path ->
            if (File(path).isFile) return path
        }
        val bytes = readClasspathBytes(
            listOf(
                "app_window_icon.png",
                "app_window_icon.webp",
                "app_icon.png",
                "app_icon.webp",
            ),
        ) ?: return null
        val file = File(System.getProperty("java.io.tmpdir"), "fromchat-notification-icon.png")
        return runCatching {
            file.writeBytes(bytes)
            file.deleteOnExit()
            cachedPath = file.absolutePath
            file.absolutePath
        }.getOrNull()
    }

    private fun readClasspathBytes(names: List<String>): ByteArray? {
        val loaders = listOfNotNull(
            Thread.currentThread().contextClassLoader,
            runCatching { Class.forName("ru.fromchat.desktop.MainKt").classLoader }.getOrNull(),
            DesktopNotificationIcon::class.java.classLoader,
            ClassLoader.getSystemClassLoader(),
        ).distinct()
        System.getProperty("compose.application.resources.dir")
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }
            ?.let { resourcesDir ->
                for (name in names) {
                    val file = File(resourcesDir, name)
                    if (file.isFile && file.length() > 0) {
                        return file.readBytes()
                    }
                }
            }
        for (name in names) {
            for (loader in loaders) {
                for (path in listOf(name, "/$name")) {
                    val bytes: ByteArray? = runCatching {
                        loader.getResourceAsStream(path)?.use { it.readBytes() }
                    }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty() &&
                        ImageIO.read(ByteArrayInputStream(bytes)) != null
                    ) {
                        return bytes
                    }
                }
            }
        }
        return null
    }
}
