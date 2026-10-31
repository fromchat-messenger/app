package ru.fromchat.ui

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Composable
actual fun UiDisplayedEffect(onDisplayed: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view, onDisplayed) {
        var notified = false

        fun tryNotify() {
            if (notified) return
            val rootInsets = ViewCompat.getRootWindowInsets(view)
            val statusTop = rootInsets
                ?.getInsets(WindowInsetsCompat.Type.statusBars())
                ?.top ?: 0
            if (statusTop <= 0) return
            notified = true
            onDisplayed()
        }

        val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            tryNotify()
            true
        }
        view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        view.post { tryNotify() }

        onDispose {
            view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
    }
}
