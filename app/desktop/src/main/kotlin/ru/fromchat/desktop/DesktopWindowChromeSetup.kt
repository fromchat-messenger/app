package ru.fromchat.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.zIndex
import androidx.compose.ui.awt.ComposeWindow
import ru.fromchat.Logger
import ru.fromchat.api.local.db.isSqliteBusy
import ru.fromchat.ui.FromChatTheme
import ru.fromchat.ui.LocalExtraStatusBarTop
import ru.fromchat.ui.getColorScheme
import java.awt.image.BufferedImage

internal fun desktopThemeBackgroundCompose(): Color =
    if (desktopAppDarkTheme()) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)

@OptIn(ExperimentalComposeUiApi::class)
internal fun installDesktopWindowExceptionHandler(window: ComposeWindow) {
    window.exceptionHandler = WindowExceptionHandler { throwable ->
        if (isSqliteBusy(throwable)) {
            if (!DatabaseLockGate.onSqliteBusy(throwable)) {
                Logger.w("DesktopWindow", "Suppressed SQLITE_BUSY in composition", throwable)
            }
        } else {
            Logger.e("DesktopWindow", "uncaught in composition", throwable)
        }
    }
}

@Composable
internal fun FrameWindowScope.DesktopRootSurface(
    appName: String,
    windowIcon: Painter,
    dockIconImage: BufferedImage?,
    windows: Boolean,
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val windowChrome = desktopThemeBackgroundCompose()

    LaunchedEffect(window, appName, windowChrome) {
        window.title = appName
        windowChrome.toAwtColor().also {
            window.background = it
            window.contentPane.background = it
            if (windows) {
                updateWindowsNativeCaptionBackground(window, it)
            }
        }

        if (windows) {
            installWindowsNativeCaptionChrome(window)
            applyWindowsRoundedCorners(window)
        }
        applyDesktopEdgeToEdgeChrome(window.rootPane)
        dockIconImage?.let { image ->
            window.iconImages = listOf(image)
            applyDockIcon(image)
        }
    }

    DisposableEffect(window) {
        installDesktopWindowExceptionHandler(window)
        onDispose {}
    }

    FromChatTheme(darkTheme = desktopAppDarkTheme()) {
        CompositionLocalProvider(
            LocalExtraStatusBarTop provides when {
                windows -> WindowsTitleBarHeight
                else -> 0.dp
            },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(windowChrome),
            ) {
                content()
                if (windows) {
                    MaterialTheme(colorScheme = getColorScheme(desktopAppDarkTheme(), dynamicColor = false)) {
                        WindowsDesktopTitleBar(
                            title = appName,
                            windowIcon = windowIcon,
                            window = window,
                            windowState = windowState,
                            onCloseRequest = onCloseRequest,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .zIndex(10_000f),
                        )
                    }
                }
            }
        }
    }
}

private fun Color.toAwtColor() =
    java.awt.Color(red, green, blue, alpha)
