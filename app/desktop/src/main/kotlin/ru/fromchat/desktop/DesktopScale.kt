package ru.fromchat.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.fromchat.logging.Logger
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Desktop scale factor for Linux desktops whose X11/Wayland handoff does not carry one.
 *
 * **Crisp fractional UI** on GNOME Wayland needs the client to render at mutter's logical scale,
 * not a 1× Skiko buffer upscaled by mutter (blurry). With Mutter's `xwayland-native-scaling`
 * (GNOME 47+), XWayland updates `Xft.dpi` / Skiko's scene density after "Keep Changes"; we then
 * pass through [androidx.compose.ui.platform.LocalDensity] unchanged. While XWayland still
 * reports 1.0 (live preview), we inflate layout to mutter's `<scale>` from `monitors.xml` so the
 * app matches GTK clients — vector layout, not compositor framebuffer scaling.
 *
 * **Both GNOME modes** (Settings → "Built-in Scaling for Legacy Apps" / `xwayland-native-scaling`):
 * - **On:** XWayland reports fractional DPI; pass through scene density when it matches mutter.
 * - **Off:** mutter framebuffer-scales XWayland; use Compose inflation only during display preview,
 *   then `1.0×` layout on commit so mutter applies scale exactly once.
 */
internal object DesktopScale {
    /** Baked into the dev deb for run-linux-prl payload verification. */
    const val LINUX_SCALE_PAYLOAD_MARKER = "fromchat-linux-scale-dual-gnome-v3"

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 5f
    private const val POLL_INTERVAL_MS = 2_000L
    private const val DEVICE_MATCH_EPSILON = 0.04f

    private val forced = MutableStateFlow<Float?>(null)

    val forcedScale: StateFlow<Float?> = forced.asStateFlow()

    private val layoutRevisionState = MutableStateFlow(0)

    val layoutRevision: StateFlow<Int> = layoutRevisionState.asStateFlow()

    /** GNOME applied scale (Keep Changes), not live display-settings preview. */
    private val gnomeDisplayScaleCommittedState = MutableStateFlow(true)

    val gnomeDisplayScaleCommitted: StateFlow<Boolean> = gnomeDisplayScaleCommittedState.asStateFlow()

    private val mutterNativeLegacyScalingState = MutableStateFlow(false)

    val mutterNativeLegacyScaling: StateFlow<Boolean> = mutterNativeLegacyScalingState.asStateFlow()

    private val watcherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lastPolledAwtScale = -1f
    private var lastMonitorsScale: Float? = null
    private var lastMonitorsMtime: Long = 0L

    val factor: Float? get() = forced.value

    val awtScale: Float
        get() = runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice
                .defaultConfiguration
                .defaultTransform
                .scaleX
                .toFloat()
        }.getOrDefault(1f).takeIf { it.isFinite() && it > 0f } ?: 1f

    val contentDensity: Float get() = factor ?: awtScale

    fun awtInflatedBeyondMutter(): Boolean {
        val forced = factor ?: return false
        return awtScale > forced + 0.05f
    }

    enum class GnomeLayoutMode {
        /** Skiko/AWT already at mutter logical density (legacy scaling on). */
        NativeScene,
        /** Display preview or waiting for XWayland DPI — Compose carries mutter scale. */
        ComposeInflate,
        /** Legacy scaling off — mutter framebuffer owns scale after commit. */
        GnomeFramebuffer,
    }

    fun gnomeLayoutMode(ambient: androidx.compose.ui.unit.Density): GnomeLayoutMode {
        val forced = factor ?: return GnomeLayoutMode.NativeScene
        val device = maxOf(ambient.density, awtScale)
        if (mutterNativeLegacyScalingState.value && device + DEVICE_MATCH_EPSILON >= forced) {
            return GnomeLayoutMode.NativeScene
        }
        if (!mutterNativeLegacyScalingState.value &&
            gnomeDisplayScaleCommittedState.value &&
            device + DEVICE_MATCH_EPSILON < forced
        ) {
            return GnomeLayoutMode.GnomeFramebuffer
        }
        return GnomeLayoutMode.ComposeInflate
    }

    fun layoutDensity(ambient: androidx.compose.ui.unit.Density): androidx.compose.ui.unit.Density {
        val forced = factor ?: return ambient
        return when (gnomeLayoutMode(ambient)) {
            GnomeLayoutMode.NativeScene -> ambient
            GnomeLayoutMode.GnomeFramebuffer -> androidx.compose.ui.unit.Density(1f, 1f)
            GnomeLayoutMode.ComposeInflate ->
                androidx.compose.ui.unit.Density(forced.coerceIn(MIN_SCALE, MAX_SCALE), 1f)
        }
    }

    val sizeRatio: Float
        get() {
            if (isLinuxOs()) return 1f
            val content = contentDensity
            val awt = awtScale
            if (awt <= 0f) return 1f
            return content / awt
        }

    fun isSupportedPlatform(): Boolean = isLinuxOs()

    fun logLinuxScalingEnvironmentOnce() {
        if (!isLinuxOs()) return
        val native = readMutterExperimentalFeatures()
        Logger.i(
            "DesktopScale",
            "$LINUX_SCALE_PAYLOAD_MARKER wayland=${!System.getenv("WAYLAND_DISPLAY").isNullOrBlank()} " +
                "mutter.experimental-features=${native ?: "n/a"} " +
                "xwaylandNativeScaling=${native?.contains("xwayland-native-scaling") == true}",
        )
    }

    fun notifyAwtScaleReady() {
        if (!isLinuxOs()) return
        val next = detect()
        val previous = forced.value
        forced.value = next
        if (previous == null && next == null) return
        if (next != null && (previous == null || abs(next - previous) > 0.01f)) {
            Logger.i("DesktopScale", "desktop scale after AWT init -> $next (was $previous)")
        }
        Logger.i(
            "DesktopScale",
            "startup awtScale=$awtScale contentDensity=$contentDensity sizeRatio=$sizeRatio factor=$factor",
        )
    }

    fun startWatching(scope: CoroutineScope = watcherScope) {
        if (!isLinuxOs()) return
        logLinuxScalingEnvironmentOnce()
        tickScale()
        scope.launch(Dispatchers.IO) {
            val home = System.getProperty("user.home")
            val configDir = home?.let { File(it, ".config") }
            if (configDir != null && configDir.isDirectory) {
                runCatching {
                    val watchService = FileSystems.getDefault().newWatchService()
                    configDir.toPath().register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE,
                    )
                    while (isActive) {
                        val key = watchService.poll(500, TimeUnit.MILLISECONDS) ?: continue
                        key.pollEvents().forEach { event ->
                            val name = event.context()?.toString() ?: return@forEach
                            if (name != "monitors.xml" && name != "gsd-monitors.xml") return@forEach
                            tickScale()
                        }
                        if (!key.reset()) break
                    }
                }.onFailure {
                    Logger.w("DesktopScale", "monitors.xml watch failed: ${it.message}")
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(POLL_INTERVAL_MS.milliseconds)
                val awt = awtScale
                if (lastPolledAwtScale < 0f || abs(awt - lastPolledAwtScale) > 0.01f) {
                    lastPolledAwtScale = awt
                    layoutRevisionState.value++
                    Logger.d(
                        "DesktopScale",
                        "awt device scale -> $awt (layout revision ${layoutRevisionState.value})",
                    )
                }
                tickScale()
            }
        }
    }

    private fun tickScale() {
        val policyBefore = mutterNativeLegacyScalingState.value to gnomeDisplayScaleCommittedState.value
        val next = detect()
        applyScaleSignal(next, policyBefore)
    }

    private fun applyScaleSignal(
        next: Float?,
        policyBefore: Pair<Boolean, Boolean>,
    ) {
        val previous = forced.value
        when {
            previous == null && next == null -> {
                if (policyBefore != mutterNativeLegacyScalingState.value to gnomeDisplayScaleCommittedState.value) {
                    layoutRevisionState.value++
                }
                return
            }
            next == null -> {
                Logger.i("DesktopScale", "scale signal lost; reverting to Compose default")
                forced.value = null
                layoutRevisionState.value++
            }
            previous == null || abs(next - previous) > 0.01f -> {
                Logger.i("DesktopScale", "desktop scale changed -> $next")
                forced.value = next
                layoutRevisionState.value++
            }
        }
        if (policyBefore != mutterNativeLegacyScalingState.value to gnomeDisplayScaleCommittedState.value) {
            layoutRevisionState.value++
            Logger.i(
                "DesktopScale",
                "gnome policy legacyNativeScaling=${mutterNativeLegacyScalingState.value} " +
                    "committed=${gnomeDisplayScaleCommittedState.value} factor=$next",
            )
        }
    }

    private fun noteMonitorsXmlWrite(scale: Float?, mtime: Long) {
        if (scale == null || !scale.isFinite() || scale <= 0f) return
        val sameScale = lastMonitorsScale != null && abs(scale - lastMonitorsScale!!) < 0.01f
        val rewritten = lastMonitorsMtime > 0L && mtime > lastMonitorsMtime
        val committedBefore = gnomeDisplayScaleCommittedState.value
        when {
            lastMonitorsScale == null -> gnomeDisplayScaleCommittedState.value = true
            sameScale && rewritten -> gnomeDisplayScaleCommittedState.value = true
            !sameScale -> gnomeDisplayScaleCommittedState.value = false
        }
        if (gnomeDisplayScaleCommittedState.value != committedBefore) {
            Logger.i(
                "DesktopScale",
                "monitors.xml write scale=$scale preview=${!gnomeDisplayScaleCommittedState.value} " +
                    "commit=$rewritten",
            )
        }
        lastMonitorsMtime = mtime
        lastMonitorsScale = scale
    }

    private fun refreshMutterNativeLegacyScalingFlag() {
        val enabled = readMutterExperimentalFeatures()?.contains("xwayland-native-scaling") == true
        if (mutterNativeLegacyScalingState.value != enabled) {
            mutterNativeLegacyScalingState.value = enabled
        }
    }

    private fun detect(): Float? {
        refreshMutterNativeLegacyScalingFlag()
        val monitorsFile = primaryMonitorsXmlFile()
        val monitors = monitorsXmlScale()
        if (monitorsFile != null && monitors != null) {
            noteMonitorsXmlWrite(monitors, monitorsFile.lastModified())
        }
        val env = envScale()
        val rendered = "env=${env?.let { trim(it) } ?: "n/a"}, monitors.xml=${monitors?.let { trim(it) } ?: "n/a"}"
        val signal = when {
            monitors != null && monitors.isFinite() && monitors in MIN_SCALE..MAX_SCALE -> monitors
            env != null && env.isFinite() && env in MIN_SCALE..MAX_SCALE -> env
            else -> {
                Logger.d("DesktopScale", "no usable scale signal ($rendered)")
                return null
            }
        }
        Logger.d(
            "DesktopScale",
            "scale=${trim(signal)} (candidates: $rendered, awt=$awtScale)",
        )
        return signal
    }

    private fun readMutterExperimentalFeatures(): String? {
        return runCatching {
            val process = ProcessBuilder("gsettings", "get", "org.gnome.mutter", "experimental-features")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun trim(value: Float): String =
        if (abs(value - kotlin.math.round(value)) < 0.01f) {
            kotlin.math.round(value).toInt().toString()
        } else {
            String.format(java.util.Locale.ROOT, "%.4f", value)
        }

    private fun envScale(): Float? {
        val gdkScale = System.getenv("GDK_SCALE")?.trim()?.toFloatOrNull() ?: return null
        val dpiScale = System.getenv("GDK_DPI_SCALE")?.trim()?.toFloatOrNull() ?: 1f
        val product = gdkScale * dpiScale
        return product.takeIf { it > 0f }
    }

    data class GdkScaleEnv(val gdkScale: Int, val dpiScale: Float) {
        val product: Float get() = gdkScale * dpiScale
    }

    fun gdkEnvFor(product: Float): GdkScaleEnv? {
        if (!product.isFinite() || product <= 1f) return null
        val gdkScale = kotlin.math.ceil(product.toDouble()).toInt().coerceAtLeast(2)
        val dpiScale = product / gdkScale
        return GdkScaleEnv(gdkScale, dpiScale)
    }

    fun java2dUiScaleProperty(product: Float): String? {
        if (!product.isFinite() || product <= 1f) return null
        if (kotlin.math.abs(product - kotlin.math.round(product)) >= 0.01f) return null
        return kotlin.math.round(product).toInt().toString()
    }

    internal fun readMonitorsXmlScale(): Float? = monitorsXmlScale()

    private fun primaryMonitorsXmlFile(): File? {
        val home = System.getProperty("user.home") ?: return null
        val preferred = File(home, ".config/monitors.xml")
        if (preferred.isFile) return preferred
        return File(home, ".config/gsd-monitors.xml").takeIf { it.isFile }
    }

    private fun monitorsXmlScale(): Float? {
        val home = System.getProperty("user.home") ?: return null
        val candidates = listOf(
            File(home, ".config/monitors.xml"),
            File(home, ".config/gsd-monitors.xml"),
        )
        val block = Regex("""<logicalmonitor>(.*?)</logicalmonitor>""", RegexOption.DOT_MATCHES_ALL)
        val scaleTag = Regex("""<scale>([0-9.]+)</scale>""")
        for (file in candidates) {
            val scale = runCatching {
                val text = file.takeIf { it.isFile }?.readText() ?: return@runCatching null
                val blocks = block.findAll(text).map { it.groupValues[1] }.toList()
                val primary = blocks.firstOrNull { it.contains("<primary>yes</primary>") }
                scaleTag.find(primary ?: blocks.firstOrNull() ?: return@runCatching null)
                    ?.groupValues?.get(1)?.toFloatOrNull()
            }.getOrNull()
            if (scale != null && scale > 0f) return scale
        }
        return null
    }

    private fun abs(value: Float): Float = if (value < 0f) -value else value
}