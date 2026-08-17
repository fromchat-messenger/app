package ru.fromchat.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import java.awt.Window
import javax.swing.RootPaneContainer

@Composable
actual fun getColorScheme(darkTheme: Boolean, dynamicColor: Boolean) =
    if (darkTheme) darkColorScheme() else lightColorScheme()

@Composable
actual fun ApplySystemBarTheme(darkTheme: Boolean, surfaceColor: Color) {
    SideEffect {
        applyMacOsAppAppearance(darkTheme)
    }
}

internal fun applyMacOsAppAppearance(dark: Boolean) {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    if (!os.contains("mac")) return
    val appearance = if (dark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"
    System.setProperty("apple.awt.application.appearance", appearance)
    for (window in Window.getWindows()) {
        if (window is RootPaneContainer) {
            window.rootPane.putClientProperty("apple.awt.application.appearance", appearance)
            window.rootPane.putClientProperty("apple.awt.windowAppearance", appearance)
        }
    }
}
