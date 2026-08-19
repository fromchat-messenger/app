package ru.fromchat.desktop

import ru.fromchat.Logger
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Registers FromChat with macOS Notification Center ([UNUserNotificationCenter])
 * and delivers native banners that appear under our bundle id, not "java".
 */
object MacNotificationCenter {
    private const val TAG = "MacNotificationCenter"
    private const val LIBRARY = "fromchat_notifications"
    private const val SPURIOUS_ACTIVATION_MS = 5_000L

    @Volatile
    var onActivated: ((String?) -> Unit)? = null

    @Volatile
    private var lastDeliverId: String? = null

    @Volatile
    private var lastDeliverAtMs: Long = 0L

    @Volatile
    private var lastDeliverWhileFrontmost: Boolean = false

    @Volatile
    private var lastWillPresentId: String? = null

    @Volatile
    private var lastWillPresentAtMs: Long = 0L

    private val available: Boolean by lazy { loadLibrary() }

    fun isAvailable(): Boolean = available

    fun registerAndRequestAuthorization(): Boolean {
        if (!available) {
            Logger.w(TAG, "register skip: native library unavailable")
            return false
        }
        Logger.i(TAG, "register begin ${runCatching { nativeDebugInfo() }.getOrDefault("debug=fail")}")
        if (!runCatching { nativeIsBundled() }.getOrDefault(false)) {
            Logger.w(TAG, "skip UNUserNotificationCenter: process is not an .app bundle")
            return false
        }
        runCatching { nativeRegisterBundle() }
        val granted = runCatching { nativeRequestAuthorization() }.getOrDefault(false)
        val status = runCatching { nativeAuthorizationStatus() }.getOrDefault(-1)
        Logger.i(TAG, "register done granted=$granted auth=${authStatusName(status)}")
        if (!granted && status == 1) {
            Logger.w(TAG, "authorization denied — opening Notification settings")
            openSystemSettings()
        }
        return granted
    }

    fun isAuthorized(): Boolean {
        if (!available) return true
        return runCatching { nativeAuthorizationStatus() }.getOrDefault(0) >= 2
    }

    fun isAppFrontmost(): Boolean =
        available && runCatching { nativeIsAppFrontmost() }.getOrDefault(false)

    /** Resigns key status so Notification Center can show banners when another app is in use. */
    fun resignActive() {
        if (!available) return
        runCatching { nativeResignActive() }
    }

    /** Lets the real frontmost app own activation so Notification Center uses banners. */
    fun yieldActivationIfNotFrontmost() {
        if (!available) return
        runCatching { nativeYieldActivation() }
    }

    fun deliver(
        title: String,
        body: String,
        subtitle: String = "",
        identifier: String = System.nanoTime().toString(),
        playSound: Boolean = true,
    ): Boolean {
        if (!available) {
            Logger.i(TAG, "deliver skip: native library unavailable")
            return false
        }
        if (!runCatching { nativeIsBundled() }.getOrDefault(false)) {
            Logger.i(
                TAG,
                "deliver skip: not bundled ${runCatching { nativeDebugInfo() }.getOrDefault("")}",
            )
            return false
        }
        val status = runCatching { nativeAuthorizationStatus() }.getOrDefault(-1)
        if (status < 2) {
            Logger.i(TAG, "deliver skip: auth=${authStatusName(status)} id=$identifier")
            return false
        }
        lastDeliverId = identifier
        lastDeliverAtMs = System.currentTimeMillis()
        val windowFocused = DesktopAppVisibility.isWindowFocused
        lastDeliverWhileFrontmost = windowFocused
        Logger.i(
            TAG,
            "deliver begin auth=${authStatusName(status)} id=$identifier " +
                "titleLen=${title.length} subtitleLen=${subtitle.length} bodyLen=${body.length} " +
                "windowFocused=$windowFocused frontmost=$lastDeliverWhileFrontmost " +
                runCatching { nativeDebugInfo() }.getOrDefault(""),
        )
        val ok = runCatching {
            nativeDeliver(title, body, subtitle, identifier, playSound, windowFocused)
        }
            .onFailure { Logger.e(TAG, "deliver native threw", it) }
            .getOrDefault(false)
        Logger.i(TAG, "deliver end ok=$ok id=$identifier")
        return ok
    }

    fun removeAll() {
        if (!available) return
        Logger.i(TAG, "removeAll")
        runCatching { nativeRemoveAll() }
    }

    fun remove(identifier: String) {
        if (!available) return
        Logger.i(TAG, "remove id=$identifier")
        runCatching { nativeRemove(arrayOf(identifier)) }
    }

    fun openSystemSettings(): Boolean {
        if (available) {
            val native = runCatching { nativeOpenSettings() }.getOrDefault(false)
            if (native) return true
        }
        return runCatching {
            ProcessBuilder(
                "open",
                "x-apple.systempreferences:com.apple.Notifications-Settings.extension?id=ru.fromchat.desktop",
            ).start().waitFor() == 0
        }.getOrDefault(false)
    }

    @JvmStatic
    fun onNativeActivated(identifier: String?) {
        val now = System.currentTimeMillis()
        val deliverElapsed = now - lastDeliverAtMs
        val presentElapsed = now - lastWillPresentAtMs
        val fromForegroundDeliver = lastDeliverWhileFrontmost &&
            identifier != null &&
            identifier == lastDeliverId &&
            deliverElapsed in 0 until SPURIOUS_ACTIVATION_MS
        val fromWillPresent = identifier != null &&
            identifier == lastWillPresentId &&
            presentElapsed in 0 until SPURIOUS_ACTIVATION_MS
        if (fromForegroundDeliver || fromWillPresent) {
            Logger.i(
                TAG,
                "ignore spurious activation id=$identifier " +
                    "deliverElapsedMs=$deliverElapsed presentElapsedMs=$presentElapsed",
            )
            return
        }
        Logger.i(TAG, "notification activated id=$identifier")
        val callback = onActivated ?: return
        java.awt.EventQueue.invokeLater {
            runCatching { callback(identifier) }.onFailure {
                Logger.e(TAG, "onActivated failed", it)
            }
        }
    }

    @JvmStatic
    fun onNativeWillPresent(identifier: String?) {
        lastWillPresentId = identifier
        lastWillPresentAtMs = System.currentTimeMillis()
        Logger.i(TAG, "willPresent id=$identifier")
    }

    private fun loadLibrary(): Boolean {
        if (!isMacOs()) return false
        return runCatching {
            val extracted = extractDylib()
            if (extracted != null) {
                System.load(extracted)
            } else {
                System.loadLibrary(LIBRARY)
            }
            Logger.i(TAG, "native library loaded")
            true
        }.onFailure {
            Logger.w(TAG, "native notifications unavailable: ${it.message}")
        }.getOrDefault(false)
    }

    private fun extractDylib(): String? {
        val resourceNames = listOf(
            "/natives/lib$LIBRARY.dylib",
            "/lib$LIBRARY.dylib",
        )
        val stream = resourceNames.firstNotNullOfOrNull { name ->
            MacNotificationCenter::class.java.getResourceAsStream(name)
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream(name.removePrefix("/"))
        } ?: run {
            val packaged = System.getProperty("compose.application.resources.dir")
                ?.let { java.io.File(it, "lib$LIBRARY.dylib") }
            return packaged?.takeIf { it.isFile }?.absolutePath
        }
        val tmp = Files.createTempFile("lib$LIBRARY", ".dylib")
        stream.use { input ->
            Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING)
        }
        tmp.toFile().deleteOnExit()
        return tmp.toAbsolutePath().toString()
    }

    private fun isMacOs(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    private fun authStatusName(status: Int): String = when (status) {
        0 -> "notDetermined"
        1 -> "denied"
        2 -> "authorized"
        3 -> "provisional"
        4 -> "ephemeral"
        else -> "unknown($status)"
    }

    @JvmStatic
    private external fun nativeRequestAuthorization(): Boolean

    @JvmStatic
    private external fun nativeAuthorizationStatus(): Int

    @JvmStatic
    private external fun nativeDeliver(
        title: String,
        body: String,
        subtitle: String,
        identifier: String,
        playSound: Boolean,
        windowFocused: Boolean,
    ): Boolean

    @JvmStatic
    private external fun nativeRemoveAll()

    @JvmStatic
    private external fun nativeRemove(identifiers: Array<String>)

    @JvmStatic
    private external fun nativeOpenSettings(): Boolean

    @JvmStatic
    private external fun nativeRegisterBundle()

    @JvmStatic
    private external fun nativeIsAppFrontmost(): Boolean

    @JvmStatic
    private external fun nativeResignActive()

    @JvmStatic
    private external fun nativeYieldActivation()

    @JvmStatic
    private external fun nativeIsBundled(): Boolean

    @JvmStatic
    private external fun nativeDebugInfo(): String
}
