package ru.fromchat.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.pr0gramm3r101.utils.UtilsLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.AppBuildInfo
import ru.fromchat.AppForeground
import ru.fromchat.Logger
import ru.fromchat.Res
import ru.fromchat.action_copy
import ru.fromchat.action_select
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.db.store.ConnectionStateStore
import ru.fromchat.api.local.db.store.ConnectionStatus
import ru.fromchat.api.local.workers.AttachmentTransferBootstrap
import ru.fromchat.app_name
import ru.fromchat.app_name_beta
import ru.fromchat.config.Settings
import ru.fromchat.desktop_about_app
import ru.fromchat.desktop_cut
import ru.fromchat.desktop_menu_edit
import ru.fromchat.desktop_menu_file
import ru.fromchat.desktop_menu_window
import ru.fromchat.desktop_minimize
import ru.fromchat.desktop_new_chat
import ru.fromchat.desktop_paste
import ru.fromchat.desktop_quit
import ru.fromchat.desktop_search_conversations
import ru.fromchat.desktop_select_all
import ru.fromchat.desktop_show_app
import ru.fromchat.desktop_tray_show
import ru.fromchat.desktop_zoom
import ru.fromchat.status_connected
import ru.fromchat.status_connecting
import ru.fromchat.status_disconnected
import ru.fromchat.ui.App
import ru.fromchat.ui.LocalExtraStatusBarTop
import ru.fromchat.ui.Theme
import java.awt.Desktop
import java.awt.Image
import java.awt.KeyboardFocusManager
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.desktop.AppReopenedListener
import java.awt.event.ActionEvent
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import javax.swing.text.DefaultEditorKit
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.skia.Image as SkiaImage

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

    fun register() {
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
        val appsDir = File(
            System.getProperty("user.home"),
            ".local/share/applications"
        ).also {
            it.mkdirs()
        }

        val desktop = File(appsDir, "fromchat-url-handler.desktop").also {
            it.writeText(
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
        }

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
    // Do NOT set compose.layers.type=WINDOW.
    // On macOS Metal that mode creates a JWindow + MetalRedrawer per Popup/Dialog; native
    // IOAccelerator surfaces are retained after dismiss and grew to multi-GB (Activity Monitor
    // ~7–8 GB) while the Java heap stayed small. Default COMPONENT layers avoid that leak;
    // context menus already use PopupProperties(clippingEnabled = false).
    if (isMacOs()) {
        // Must be set before AWT Toolkit init (do not call getString / Compose here).
        // Matches Res.string.app_name / app_name_beta.
        System.setProperty(
            "apple.awt.application.name",
            if (AppBuildInfo.isDebug) "FromChat Beta" else "FromChat",
        )
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        // Treat TrayIcon images as NSImage templates so the menu bar tints them for light/dark.
        // Must be set before CTrayIcon loads (Compose Tray / SystemTray).
        System.setProperty("apple.awt.enableTemplateImages", "true")
    }
    if (!DesktopSingleInstance.acquireOrForward(args)) return
    DesktopProtocolRegistration.register()
    // Close-to-tray keeps the process + tray; no SMAppService / Login Items registration.
    args.filter { DesktopDeepLinkBus.isFromChatUri(it) }
        .forEach { DesktopDeepLinkBus.handleUri(it) }

    // Avoid the AWT default white "control" flash before Compose paints (CMP #1794).
    applyDesktopWindowChromeBackground()

    val dockIconImage = loadAppIconBufferedImage(tray = false)
    applyDockIcon(dockIconImage)
    // Re-apply after AWT is fully up — Taskbar can ignore early sets on macOS :run.
    SwingUtilities.invokeLater { applyDockIcon(dockIconImage) }

    application {
        UtilsLibrary.init()
        DesktopApplicationBootstrap.launchOnApplicationStart()

        val windowState = rememberWindowState(
            size = remember { DesktopWindowPrefs.loadSize() },
            position = remember { DesktopWindowPrefs.loadPosition() },
        )
        // Hide until App bootstrap sets startDestination (avoids ~2s empty/white window).
        var contentReady by remember { mutableStateOf(false) }
        var windowVisible by remember { mutableStateOf(true) }
        val trayState = rememberTrayState()
        // BufferedImage.toPainter() keeps the AWT pixels (avoids blank BitmapPainter round-trip).
        val trayIcon = remember { loadAppIconPainter(tray = true) }
        val windowIcon = remember {
            dockIconImage?.toPainter() ?: loadAppIconPainter(tray = false)
        }
        val traySupported = remember { java.awt.SystemTray.isSupported() }
        val mac = remember { isMacOs() }
        val appName = stringResource(
            if (AppBuildInfo.isDebug) Res.string.app_name_beta else Res.string.app_name,
        )
        val aboutApp = stringResource(Res.string.desktop_about_app)
        val trayShow = stringResource(Res.string.desktop_tray_show)
        val quit = stringResource(Res.string.desktop_quit)
        val connectionStatus by ConnectionStateStore.status.collectAsState()
        var wsLinked by remember { mutableStateOf(WebSocketManager.isConnected) }
        // ApiClient.token is not a Compose state; poll so menu items update on login/logout.
        // Only write when the value changes — avoids MenuBar/Tray recomposition churn every tick.
        var isLoggedIn by remember { mutableStateOf(!ApiClient.token.isNullOrBlank()) }
        LaunchedEffect(Unit) {
            while (true) {
                val linked = WebSocketManager.isConnected
                if (wsLinked != linked) wsLinked = linked
                val loggedIn = !ApiClient.token.isNullOrBlank()
                if (isLoggedIn != loggedIn) isLoggedIn = loggedIn
                delay(400.milliseconds)
            }
        }
        // Mirror ConnectionStateStore + WebSocketManager.isConnected (+ logged-out → disconnected).
        val wsStatusLabel = when {
            connectionStatus == ConnectionStatus.CONNECTED ||
                connectionStatus == ConnectionStatus.UPDATING ||
                wsLinked -> stringResource(Res.string.status_connected)
            ApiClient.token.isNullOrEmpty() -> stringResource(Res.string.status_disconnected)
            else -> stringResource(Res.string.status_connecting)
        }

        val windowChrome = remember { desktopThemeBackgroundCompose() }

        LaunchedEffect(Unit) {
            // Safety: never leave the window stuck hidden if bootstrap hangs.
            delay(8_000.milliseconds)
            contentReady = true
        }

        LaunchedEffect(windowState.size, windowState.position) {
            delay(3_000.milliseconds)
            DesktopWindowPrefs.save(windowState.size, windowState.position)
        }

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

        fun showMainWindow() {
            windowVisible = true
            AppForeground.setForeground(true)
        }

        fun openAbout() {
            showMainWindow()
            DesktopMenuCommands.emit(DesktopMenuCommand.OpenAbout)
        }

        /** Hide window, keep process + tray (close-to-background). Else quit. */
        fun requestCloseToBackgroundOrQuit() {
            if (traySupported) {
                windowVisible = false
                // Desktop keeps sockets alive while the process runs (see keepWebSocketAliveInBackground).
                AppForeground.setForeground(true)
                if (mac) {
                    Logger.i(MacBackgroundLifecycle.LOG_TAG, "Window closed → background (tray kept, process alive)")
                }
            } else {
                exitApplication()
            }
        }

        LaunchedEffect(Unit) {
            if (!Desktop.isDesktopSupported()) return@LaunchedEffect
            val desktop = Desktop.getDesktop()
            if (mac && desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler {
                    SwingUtilities.invokeLater { openAbout() }
                }
            }
            // ⌘Q / system Quit must fully exit (not close-to-tray).
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                desktop.setQuitHandler { _, response ->
                    DesktopSingleInstance.release()
                    response.performQuit()
                    exitApplication()
                }
            }
            // Dock click while window is hidden → show again (macOS AppReopened).
            if (mac) {
                desktop.addAppEventListener(
                    AppReopenedListener {
                        SwingUtilities.invokeLater { showMainWindow() }
                    },
                )
            }
        }

        if (traySupported) {
            Tray(
                icon = trayIcon,
                state = trayState,
                tooltip = appName,
                onAction = { showMainWindow() },
                menu = {
                    Item(wsStatusLabel, enabled = false) {}
                    Separator()
                    Item(trayShow) { showMainWindow() }
                    Item(aboutApp) { openAbout() }
                    Separator()
                    Item(quit) { exitApplication() }
                },
            )
        }

        Window(
            onCloseRequest = { requestCloseToBackgroundOrQuit() },
            title = appName,
            state = windowState,
            visible = contentReady && (windowVisible || !traySupported),
            icon = windowIcon,
            onPreviewKeyEvent = { event ->
                // macOS: MenuBar KeyShortcuts handle these. Win/Linux: no menu bar.
                if (mac || event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                    return@Window false
                }
                when {
                    !event.isShiftPressed && event.key == Key.N -> {
                        if (!isLoggedIn) return@Window false
                        showMainWindow()
                        DesktopMenuCommands.emit(DesktopMenuCommand.NewChat)
                        true
                    }
                    !event.isShiftPressed && event.key == Key.F -> {
                        if (!isLoggedIn) return@Window false
                        showMainWindow()
                        DesktopMenuCommands.emit(DesktopMenuCommand.SearchConversations)
                        true
                    }
                    event.isShiftPressed && event.key == Key.S -> {
                        showMainWindow()
                        DesktopMenuCommands.emit(DesktopMenuCommand.EnterChatListSelection)
                        true
                    }
                    event.isShiftPressed && event.key == Key.A -> {
                        openAbout()
                        true
                    }
                    event.isShiftPressed && event.key == Key.N -> {
                        showMainWindow()
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
            LaunchedEffect(window, appName, windowChrome) {
                window.title = appName
                windowChrome.toAwtColor().also {
                    window.background = it
                    window.contentPane.background = it
                }

                enableEdgeToEdgeTitleBar(window.rootPane)
                dockIconImage?.let { image ->
                    window.iconImages = listOf(image)
                    applyDockIcon(image)
                }
            }

            DisposableEffect(Unit) {
                AppForeground.setForeground(true)
                onDispose {}
            }

            if (mac) {
                FromChatMenuBar(
                    isLoggedIn = isLoggedIn,
                    onNewChat = {
                        showMainWindow()
                        DesktopMenuCommands.emit(DesktopMenuCommand.NewChat)
                    },
                    onSearchConversations = {
                        showMainWindow()
                        DesktopMenuCommands.emit(DesktopMenuCommand.SearchConversations)
                    },
                    onSelectChats = {
                        showMainWindow()
                        DesktopMenuCommands.emit(DesktopMenuCommand.EnterChatListSelection)
                    },
                    onShow = { showMainWindow() },
                    onMinimize = { windowState.isMinimized = true },
                    onZoom = {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Maximized) {
                                WindowPlacement.Floating
                            } else {
                                WindowPlacement.Maximized
                            }
                    },
                    onQuit = { exitApplication() },
                )
            }

            CompositionLocalProvider(
                LocalExtraStatusBarTop provides if (mac) 28.dp else 0.dp,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(windowChrome),
                ) {
                    App(onContentReady = { contentReady = true })
                }
            }
        }
    }
}

@Composable
private fun FrameWindowScope.FromChatMenuBar(
    isLoggedIn: Boolean,
    onNewChat: () -> Unit,
    onSearchConversations: () -> Unit,
    onSelectChats: () -> Unit,
    onShow: () -> Unit,
    onMinimize: () -> Unit,
    onZoom: () -> Unit,
    onQuit: () -> Unit,
) {
    val file = stringResource(Res.string.desktop_menu_file)
    val newChat = stringResource(Res.string.desktop_new_chat)
    val searchConversations = stringResource(Res.string.desktop_search_conversations)
    val quit = stringResource(Res.string.desktop_quit)
    val edit = stringResource(Res.string.desktop_menu_edit)
    val cut = stringResource(Res.string.desktop_cut)
    val copy = stringResource(Res.string.action_copy)
    val paste = stringResource(Res.string.desktop_paste)
    val selectAll = stringResource(Res.string.desktop_select_all)
    val select = stringResource(Res.string.action_select)
    val windowMenu = stringResource(Res.string.desktop_menu_window)
    val minimize = stringResource(Res.string.desktop_minimize)
    val zoom = stringResource(Res.string.desktop_zoom)
    val showApp = stringResource(Res.string.desktop_show_app)

    MenuBar {
        Menu(file, mnemonic = 'F') {
            if (isLoggedIn) {
                Item(
                    newChat,
                    shortcut = KeyShortcut(Key.N, meta = true),
                    onClick = onNewChat,
                )

                Item(
                    searchConversations,
                    shortcut = KeyShortcut(Key.F, meta = true),
                    onClick = onSearchConversations,
                )

                Separator()
            }

            Item(
                quit,
                shortcut = KeyShortcut(Key.Q, meta = true),
                onClick = onQuit,
            )
        }

        Menu(edit, mnemonic = 'E') {
            Item(
                cut,
                shortcut = KeyShortcut(Key.X, meta = true),
                onClick = { performAwtEditAction(DefaultEditorKit.cutAction) },
            )

            Item(
                copy,
                shortcut = KeyShortcut(Key.C, meta = true),
                onClick = { performAwtEditAction(DefaultEditorKit.copyAction) },
            )

            Item(
                paste,
                shortcut = KeyShortcut(Key.V, meta = true),
                onClick = { performAwtEditAction(DefaultEditorKit.pasteAction) },
            )

            Separator()

            Item(
                selectAll,
                shortcut = KeyShortcut(Key.A, meta = true),
                onClick = { performAwtEditAction(DefaultEditorKit.selectAllAction) },
            )

            Item(
                select,
                shortcut = KeyShortcut(Key.S, meta = true, shift = true),
                onClick = onSelectChats,
            )
        }

        Menu(windowMenu, mnemonic = 'W') {
            Item(
                minimize,
                shortcut = KeyShortcut(Key.M, meta = true),
                onClick = onMinimize,
            )

            Item(zoom, onClick = onZoom)

            Separator()

            Item(
                showApp,
                shortcut = KeyShortcut(Key.N, meta = true, shift = true),
                onClick = onShow,
            )
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

/**
 * JFrame uses UIManager "control" as its default background before Compose draws.
 * Set it to the app theme background so dark theme never flashes white (CMP #1794).
 *
 * Colors match Material3 [darkColorScheme]/[lightColorScheme] defaults used by [ru.fromchat.ui.getColorScheme].
 */
private fun applyDesktopWindowChromeBackground() {
    UIManager.put("control", ColorUIResource(desktopThemeBackgroundCompose().toAwtColor()))
}

private fun desktopThemeBackgroundCompose() =
    if (
        when (runCatching { Settings.theme }.getOrDefault(Theme.AsSystem)) {
            Theme.Dark -> true
            Theme.Light -> false
            Theme.AsSystem -> isSystemAppearanceDark()
        }
    ) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)

private fun Color.toAwtColor() =
    java.awt.Color(red, green, blue, alpha)

private fun isSystemAppearanceDark() = runCatching {
    if (isMacOs()) {
        ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
            .equals("Dark", ignoreCase = true)
    } else false
}.getOrDefault(false)

private fun enableEdgeToEdgeTitleBar(rootPane: JRootPane) {
    if (!isMacOs()) return
    rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
}

private fun applyDockIcon(image: BufferedImage?) {
    if (image == null) {
        Logger.w("DesktopIcon", "applyDockIcon skipped — image is null")
        return
    }

    runCatching {
        if (!Taskbar.isTaskbarSupported()) {
            Logger.w("DesktopIcon", "Taskbar unsupported; dock icon not set")
            return
        }

        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            taskbar.iconImage = image
            Logger.i("DesktopIcon", "Taskbar dock icon set (${image.width}x${image.height})")
        } else {
            Logger.w("DesktopIcon", "Taskbar.Feature.ICON_IMAGE unsupported")
        }
    }.onFailure { error ->
        Logger.w("DesktopIcon", "Failed to set dock icon: ${error.message}")
    }
}

/**
 * Loads the desktop tray or window/dock icon from packaged resources.
 * Tray prefers the flat white+alpha mark (`app_icon`, template-ready); dock/window prefer
 * the branded AppIcon (`app_window_icon`).
 *
 * Uses [BufferedImage.toPainter] so Compose Tray/Window hand the same AWT pixels to the OS
 * (BitmapPainter → ImageBitmap round-trips can rasterize blank and fall back to Compose's default).
 *
 * On macOS, `apple.awt.enableTemplateImages` makes the tray mark an NSImage template so the
 * menu bar recolors it for the current appearance (set early in [main]).
 */
private fun loadAppIconPainter(tray: Boolean): Painter {
    val image = loadAppIconBufferedImage(tray)
    if (image == null) {
        Logger.e(
            "DesktopIcon",
            "loadAppIconPainter fell through — no resource decoded (tray=$tray); using solid fallback",
        )
        return solidFallbackIcon().toPainter()
    }

    Logger.i(
        "DesktopIcon",
        "loadAppIconPainter ok tray=$tray size=${image.width}x${image.height} type=${image.type}",
    )

    return image.toPainter()
}

private fun loadAppIconBufferedImage(tray: Boolean): BufferedImage? {
    val names = if (tray) {
        listOf("app_icon.png", "app_icon.webp", "app_window_icon.png", "app_window_icon.webp")
    } else {
        listOf("app_window_icon.png", "app_window_icon.webp", "app_icon.png", "app_icon.webp")
    }

    for (name in names) {
        return ensureArgb(
            (
                decodeImageBytes(
                    (readClasspathBytes(
                        name,
                        listOfNotNull(
                            DesktopApplicationBootstrap::class.java.classLoader,
                            Thread.currentThread().contextClassLoader,
                            ClassLoader.getSystemClassLoader(),
                        ).distinct()
                    ) ?: readFileFallbackBytes(name) ?: continue)
                        .also { if (it.isEmpty()) continue },
                    name
                ) ?: continue
            ).also {
                if (it.width < 16 || it.height < 16) {
                    Logger.w(
                        "DesktopIcon",
                        "Skipping $name — decoded size ${it.width}x${it.height}"
                    )
                    continue
                }
            }
        )
            .let { if (tray) scaleBufferedImage(it, 64) else it }
            .also {
                Logger.i("DesktopIcon", "Loaded $name (${it.width}x${it.height}) tray=$tray")
            }
    }

    Logger.e("DesktopIcon", "No icon resource found for tray=$tray (tried $names)")
    return null
}

private fun readClasspathBytes(name: String, loaders: List<ClassLoader>): ByteArray? {
    val paths = listOf(name, "/$name")
    for (loader in loaders) {
        loop@ for (path in paths) {
            val bytes = runCatching {
                (loader.getResourceAsStream(path) ?: continue@loop)
                    .use { it.readBytes() }
            }.onFailure { error ->
                Logger.w("DesktopIcon", "Failed reading $path from $loader: ${error.message}")
            }
            .getOrNull()
            ?: continue

            if (bytes.isNotEmpty()) {
                Logger.i("DesktopIcon", "Classpath hit $path via $loader (${bytes.size} bytes)")
                return bytes
            }
        }
    }

    // Explicit class resource (works when context CL is a filtered compose run CL).
    loop@ for (path in paths) {
        val bytes = runCatching {
            (DesktopApplicationBootstrap::class.java.getResourceAsStream(path) ?: continue@loop)
                .use { it.readBytes() }
        }.getOrNull() ?: continue

        if (bytes.isNotEmpty()) {
            Logger.i("DesktopIcon", "Class resource hit $path (${bytes.size} bytes)")
            return bytes
        }
    }

    return null
}

/** Last resort when classpath packaging under `:run` omits resources. */
private fun readFileFallbackBytes(name: String): ByteArray? {
    for (
        file in listOf(
            File("app/desktop/src/main/resources", name),
            File("src/main/resources", name),
            File(System.getProperty("user.dir"), "src/main/resources/$name"),
            File(System.getProperty("user.dir"), "app/desktop/src/main/resources/$name"),
        )
    ) {
        if (!file.isFile) continue
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
        if (bytes.isNotEmpty()) {
            Logger.i("DesktopIcon", "File fallback hit ${file.absolutePath} (${bytes.size} bytes)")
            return bytes
        }
    }

    return null
}

private fun decodeImageBytes(bytes: ByteArray, name: String): BufferedImage? {
    val viaImageIo = runCatching {
        ImageIO.read(ByteArrayInputStream(bytes))
    }.onFailure { error ->
        Logger.w("DesktopIcon", "ImageIO failed for $name: ${error.message}")
    }.getOrNull()

    if (viaImageIo != null) return viaImageIo

    return runCatching {
        bufferedImageFromComposeArgb(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
    }.onFailure { error ->
        Logger.w("DesktopIcon", "Skia decode failed for $name: ${error.message}")
    }.getOrNull()
}

private fun ensureArgb(source: BufferedImage): BufferedImage {
    if (source.type == BufferedImage.TYPE_INT_ARGB) return source

    val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    out.createGraphics().apply {
        drawImage(source, 0, 0, null)
        dispose()
    }

    return out
}

private fun bufferedImageFromComposeArgb(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
): BufferedImage {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.readPixels(pixels)

    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    out.setRGB(0, 0, w, h, pixels, 0, w)

    return out
}

private fun scaleBufferedImage(source: BufferedImage, size: Int): BufferedImage {
    if (source.width == size && source.height == size) return source
    val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)

    out.createGraphics().apply {
        setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY,
        )
        drawImage(source.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null)

        dispose()
    }

    return out
}

/** Opaque mark so Tray never substitutes the Compose default for a blank painter. */
private fun solidFallbackIcon(): BufferedImage {
    val out = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
    out.createGraphics().apply {
        color = java.awt.Color(0x1A, 0x73, 0xE8)
        fillRoundRect(2, 2, 28, 28, 8, 8)

        dispose()
    }

    return out
}
