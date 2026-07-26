package ru.fromchat.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.pr0gramm3r101.utils.UtilsLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.fromchat.AppForeground
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.workers.AttachmentTransferBootstrap
import ru.fromchat.ui.App
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private object DesktopApplicationBootstrap {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun launchOnApplicationStart() {
        if (started) return
        started = true
        AppForeground.setForeground(true)
        scope.launch {
            runCatching { ApiClient.loadPersistedData() }
            runCatching { AttachmentTransferBootstrap.runColdStart() }
        }
    }
}

private object DesktopSingleInstance {
    private const val PORT = 38_741
    private val running = AtomicBoolean(false)

    /**
     * @return true if this process owns the instance and should show UI;
     * false if another instance is already running (deep link forwarded).
     */
    fun acquireOrForward(args: Array<String>): Boolean {
        val deepLink = args.firstOrNull { DesktopDeepLinkBus.isFromChatUri(it) }
        return try {
            val server = ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"))
            running.set(true)
            thread(name = "fromchat-deeplink-ipc", isDaemon = true) {
                while (running.get()) {
                    runCatching {
                        server.accept().use { socket ->
                            val line = BufferedReader(
                                InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8),
                            ).readLine()?.trim().orEmpty()
                            if (DesktopDeepLinkBus.isFromChatUri(line)) {
                                DesktopDeepLinkBus.handleUri(line)
                            }
                        }
                    }
                }
            }
            if (deepLink != null) {
                DesktopDeepLinkBus.handleUri(deepLink)
            }
            true
        } catch (_: Exception) {
            if (deepLink != null) {
                runCatching {
                    Socket("127.0.0.1", PORT).use { socket ->
                        OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8).use { out ->
                            out.write(deepLink)
                            out.write("\n")
                            out.flush()
                        }
                    }
                }
            }
            false
        }
    }

    fun release() {
        running.set(false)
    }
}

private object DesktopProtocolRegistration {
    private const val SCHEME = "fromchat"

    fun registerBestEffort() {
        runCatching {
            when {
                isMac() -> Unit // Packaged macOS builds declare CFBundleURLTypes in Info.plist.
                isWindows() -> registerWindows()
                isLinux() -> registerLinux()
            }
        }
    }

    private fun isMac(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun isLinux(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    private fun javaLauncherCommand(): String {
        val javaHome = System.getProperty("java.home")
        val javaBin = if (isWindows()) "$javaHome\\bin\\javaw.exe" else "$javaHome/bin/java"
        val classPath = System.getProperty("java.class.path")
        val main = "ru.fromchat.desktop.MainKt"
        return if (isWindows()) {
            "\"$javaBin\" -cp \"$classPath\" $main"
        } else {
            "$javaBin -cp ${shellQuote(classPath)} $main"
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun registerWindows() {
        val command = javaLauncherCommand() + " \"%1\""
        val base = "HKCU\\Software\\Classes\\$SCHEME"
        listOf(
            listOf("reg", "add", base, "/ve", "/d", "URL:FromChat Protocol", "/f"),
            listOf("reg", "add", base, "/v", "URL Protocol", "/d", "", "/f"),
            listOf("reg", "add", "$base\\shell\\open\\command", "/ve", "/d", command, "/f"),
        ).forEach { args ->
            ProcessBuilder(args).redirectErrorStream(true).start().waitFor()
        }
    }

    private fun registerLinux() {
        val appsDir = java.io.File(System.getProperty("user.home"), ".local/share/applications")
        appsDir.mkdirs()
        val desktop = java.io.File(appsDir, "fromchat-url-handler.desktop")
        desktop.writeText(
            """
            |[Desktop Entry]
            |Type=Application
            |Name=FromChat URL Handler
            |Exec=${javaLauncherCommand()} %u
            |StartupNotify=false
            |MimeType=x-scheme-handler/$SCHEME;
            |NoDisplay=true
            """.trimMargin(),
        )
        ProcessBuilder("xdg-mime", "default", desktop.name, "x-scheme-handler/$SCHEME")
            .redirectErrorStream(true)
            .start()
            .waitFor()
        ProcessBuilder("update-desktop-database", appsDir.absolutePath)
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }
}

fun main(args: Array<String>) {
    if (!DesktopSingleInstance.acquireOrForward(args)) return
    DesktopProtocolRegistration.registerBestEffort()
    args.filter { DesktopDeepLinkBus.isFromChatUri(it) }
        .forEach { DesktopDeepLinkBus.handleUri(it) }

    application {
        UtilsLibrary.init()
        DesktopApplicationBootstrap.launchOnApplicationStart()

        val windowState = rememberWindowState()
        var windowVisible by remember { mutableStateOf(true) }
        val trayState = rememberTrayState()
        val trayIcon = remember { createTrayIconPainter() }
        val traySupported = remember { java.awt.SystemTray.isSupported() }

        DisposableEffect(trayState) {
            DesktopNotifier.sink = { title, body ->
                trayState.sendNotification(
                    Notification(title = title, message = body),
                )
            }
            onDispose {
                DesktopNotifier.sink = null
                DesktopSingleInstance.release()
            }
        }

        if (traySupported) {
            Tray(
                icon = trayIcon,
                state = trayState,
                tooltip = "FromChat",
                onAction = { windowVisible = true },
                menu = {
                    Item("Show") { windowVisible = true }
                    Item("Quit") { exitApplication() }
                },
            )
        }

        Window(
            onCloseRequest = {
                if (traySupported) {
                    windowVisible = false
                    AppForeground.setForeground(true)
                } else {
                    exitApplication()
                }
            },
            title = "FromChat",
            state = windowState,
            visible = windowVisible || !traySupported,
            icon = trayIcon,
        ) {
            DisposableEffect(Unit) {
                AppForeground.setForeground(true)
                onDispose { }
            }
            App()
        }
    }
}

private fun createTrayIconPainter(): Painter = object : Painter() {
    override val intrinsicSize: Size = Size(32f, 32f)

    override fun DrawScope.onDraw() {
        drawRect(Color(0xFF4484F4))
    }
}
