package ru.fromchat.ui.jvm

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.web.WebEngine
import ru.fromchat.Logger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val javaFxStarted = AtomicBoolean(false)

/** Starts the JavaFX toolkit once (safe if already running via [JFXPanel]). */
fun ensureJavaFxStarted(logTag: String) {
    if (javaFxStarted.get()) return
    synchronized(javaFxStarted) {
        if (javaFxStarted.get()) return
        runCatching {
            Platform.setImplicitExit(false)
            val latch = CountDownLatch(1)
            Platform.startup { latch.countDown() }
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Logger.w(logTag, "JavaFX Platform.startup timed out")
            }
        }.onFailure { error ->
            // Toolkit may already be running via JFXPanel.
            Logger.d(logTag, "JavaFX startup: ${error.message}")
        }
        javaFxStarted.set(true)
    }
}

/**
 * Tears down an OpenJFX [WebView] hosted in a Compose [JFXPanel].
 *
 * Without this, [WebEngine]'s pulse timer and WebKit textures stay alive after the Swing panel
 * leaves composition — observed as multi-GB macOS IOAccelerator (graphics) growth while the
 * Java heap stays small.
 */
fun releaseJavaFxWebView(
    panelRef: AtomicReference<JFXPanel?>,
    engineRef: AtomicReference<WebEngine?>,
) {
    val panel = panelRef.getAndSet(null)
    val engine = engineRef.getAndSet(null)
    if (panel == null && engine == null) return
    Platform.runLater {
        runCatching {
            engine?.loadWorker?.cancel()
            engine?.load(null)
            panel?.scene = null
        }.onFailure { error ->
            Logger.w("JavaFxWebView", "release failed: ${error.message}")
        }
    }
}
