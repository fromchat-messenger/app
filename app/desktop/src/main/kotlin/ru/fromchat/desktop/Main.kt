package ru.fromchat.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.pr0gramm3r101.utils.UtilsLibrary
import java.awt.Desktop
import java.awt.KeyboardFocusManager
import java.awt.event.ActionEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import javax.swing.text.DefaultEditorKit
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import ru.fromchat.AppForeground
import ru.fromchat.Logger
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.workers.AttachmentTransferBootstrap
import ru.fromchat.ui.App
import ru.fromchat.ui.LocalExtraStatusBarTop

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
                isMacOs() -> Unit // Packaged macOS builds declare CFBundleURLTypes in Info.plist.
                isWindowsOs() -> registerWindows()
                isLinuxOs() -> registerLinux()
            }
        }
    }

    private fun javaLauncherCommand(): String {
        val javaHome = System.getProperty("java.home")
        val javaBin = if (isWindowsOs()) "$javaHome\\bin\\javaw.exe" else "$javaHome/bin/java"
        val classPath = System.getProperty("java.class.path")
        val main = "ru.fromchat.desktop.MainKt"
        return if (isWindowsOs()) {
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
    if (isMacOs()) {
        System.setProperty("apple.awt.application.name", "FromChat")
        System.setProperty("apple.laf.useScreenMenuBar", "true")
    }
    if (!DesktopSingleInstance.acquireOrForward(args)) return
    DesktopProtocolRegistration.registerBestEffort()
    args.filter { DesktopDeepLinkBus.isFromChatUri(it) }
        .forEach { DesktopDeepLinkBus.handleUri(it) }

    application {
        UtilsLibrary.init()
        DesktopApplicationBootstrap.launchOnApplicationStart()

        val windowState = rememberWindowState()
        val aboutWindowState = rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            size = DpSize(440.dp, 640.dp),
        )
        var windowVisible by remember { mutableStateOf(true) }
        var aboutOpen by remember { mutableStateOf(false) }
        val trayState = rememberTrayState()
        val trayIcon = remember { loadAppIconPainter(tray = true) }
        val windowIcon = remember { loadAppIconPainter(tray = false) }
        val traySupported = remember { java.awt.SystemTray.isSupported() }
        val mac = remember { isMacOs() }

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

        LaunchedEffect(Unit) {
            if (!mac || !Desktop.isDesktopSupported()) return@LaunchedEffect
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler {
                    SwingUtilities.invokeLater { aboutOpen = true }
                }
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
                    Item("About FromChat") { aboutOpen = true }
                    Separator()
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
            icon = windowIcon,
            onPreviewKeyEvent = { event ->
                if (mac || event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                    return@Window false
                }
                when {
                    event.isShiftPressed && event.key == Key.A -> {
                        aboutOpen = true
                        true
                    }
                    event.isShiftPressed && event.key == Key.N -> {
                        windowVisible = true
                        true
                    }
                    event.key == Key.M -> {
                        windowState.isMinimized = true
                        true
                    }
                    event.key == Key.Q -> {
                        exitApplication()
                        true
                    }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(window) {
                enableEdgeToEdgeTitleBar(window.rootPane)
            }
            DisposableEffect(Unit) {
                AppForeground.setForeground(true)
                onDispose { }
            }

            if (mac) {
                FromChatMenuBar(
                    onAbout = { aboutOpen = true },
                    onShow = { windowVisible = true },
                    onMinimize = { windowState.isMinimized = true },
                    onZoom = {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Maximized) {
                                WindowPlacement.Floating
                            } else {
                                WindowPlacement.Maximized
                            }
                    },
                )
            }

            CompositionLocalProvider(
                LocalExtraStatusBarTop provides if (mac) 28.dp else 0.dp,
            ) {
                App()
            }
        }

        if (aboutOpen) {
            Window(
                onCloseRequest = { aboutOpen = false },
                title = "About FromChat",
                state = aboutWindowState,
                icon = windowIcon,
            ) {
                DesktopAboutContent(onClose = { aboutOpen = false })
            }
        }
    }
}

@Composable
private fun FrameWindowScope.FromChatMenuBar(
    onAbout: () -> Unit,
    onShow: () -> Unit,
    onMinimize: () -> Unit,
    onZoom: () -> Unit,
) {
    MenuBar {
        Menu("Actions", mnemonic = 'A') {
            Item("About FromChat", onClick = onAbout)
            Item(
                "Show FromChat",
                shortcut = KeyShortcut(Key.N, meta = true, shift = true),
                onClick = onShow,
            )
        }
        Menu("Edit", mnemonic = 'E') {
            Item(
                "Cut",
                shortcut = KeyShortcut(Key.X, meta = true),
                onClick = { performAwtEditAction(DefaultEditorKit.cutAction) },
            )
            Item(
                "Copy",
                shortcut = KeyShortcut(Key.C, meta = true),
                onClick = { performAwtEditAction(DefaultEditorKit.copyAction) },
            )
            Item(
                "Paste",
                shortcut = KeyShortcut(Key.V, meta = true),
                onClick = { performAwtEditAction(DefaultEditorKit.pasteAction) },
            )
            Separator()
            Item(
                "Select All",
                shortcut = KeyShortcut(Key.A, meta = true),
                onClick = { performAwtEditAction(DefaultEditorKit.selectAllAction) },
            )
        }
        Menu("Window", mnemonic = 'W') {
            Item(
                "Minimize",
                shortcut = KeyShortcut(Key.M, meta = true),
                onClick = onMinimize,
            )
            Item("Zoom", onClick = onZoom)
        }
    }
}

private fun performAwtEditAction(actionName: String) {
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner as? JComponent
        ?: return
    val action = focusOwner.actionMap.get(actionName) ?: return
    action.actionPerformed(ActionEvent(focusOwner, ActionEvent.ACTION_PERFORMED, actionName))
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("mac")

private fun isWindowsOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("win")

private fun isLinuxOs(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("linux")

private fun enableEdgeToEdgeTitleBar(rootPane: JRootPane) {
    if (!isMacOs()) return
    rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
}

/**
 * Loads the desktop tray or window icon from packaged resources.
 * Prefers PNG, then WebP; returns a tiny empty bitmap only if every decode fails.
 */
private fun loadAppIconPainter(tray: Boolean): Painter {
    val names = if (tray) {
        listOf("app_icon.png", "app_icon.webp", "app_window_icon.png", "app_window_icon.webp")
    } else {
        listOf("app_window_icon.png", "app_window_icon.webp", "app_icon.png", "app_icon.webp")
    }
    val loaders = listOfNotNull(
        Thread.currentThread().contextClassLoader,
        DesktopApplicationBootstrap::class.java.classLoader,
        ClassLoader.getSystemClassLoader(),
    )

    for (name in names) {
        for (loader in loaders) {
            val stream = loader.getResourceAsStream(name)
                ?: loader.getResourceAsStream("/$name")
                ?: continue
            val bytes = runCatching { stream.use { it.readBytes() } }.getOrNull() ?: continue
            if (bytes.isEmpty()) continue
            val bitmap = runCatching {
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.onFailure { error ->
                Logger.w("DesktopIcon", "Failed to decode $name: ${error.message}")
            }.getOrNull() ?: continue
            if (bitmap.width < 16 || bitmap.height < 16) {
                Logger.w("DesktopIcon", "Skipping $name — decoded size ${bitmap.width}x${bitmap.height}")
                continue
            }
            Logger.i("DesktopIcon", "Loaded $name (${bitmap.width}x${bitmap.height}) tray=$tray")
            return BitmapPainter(bitmap)
        }
    }

    Logger.w("DesktopIcon", "No app icon resource decoded; using empty placeholder")
    return BitmapPainter(ImageBitmap(32, 32))
}
