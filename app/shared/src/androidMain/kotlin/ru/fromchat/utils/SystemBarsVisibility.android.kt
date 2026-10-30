package ru.fromchat.utils

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

@Composable
actual fun rememberSystemBarsController(): ((Boolean) -> Unit)? {
    val view = LocalView.current
    return remember(view) {
        val activity = view.context as? Activity ?: return@remember null
        val window = activity.window ?: return@remember null
        val controller = WindowCompat.getInsetsController(window, view);

        { visible: Boolean ->
            if (visible) {
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}
