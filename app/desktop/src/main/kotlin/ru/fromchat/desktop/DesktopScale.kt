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
import ru.fromchat.Logger
import java.io.File

/**
 * Desktop scale factor for Linux desktops whose X11/Wayland handoff does not carry one.
 *
 * AWT infers its device scale from the X server's reported DPI. That is unreliable here: virtual
 * displays (VMs, remote sessions, most docking stations) advertise `0mm x 0mm`, so the DPI
 * collapses to the 96 default. On those, mutter renders its own UI at the monitor's scale while
 * handing XWayland clients the full physical resolution and no scale — so a Compose window drawn at
 * 1x sits next to a correctly scaled desktop looking tiny.
 *
 * Observed on Ubuntu/GNOME at 166%: AWT reported 2.0 (it had read `Xft.dpi=192`) while Compose
 * reported 1.0. `sun.java2d.uiScale` is not a usable lever here: on X11
 * `GraphicsConfiguration.defaultTransform` is an `int`, so a fractional value truncates to 1 —
 * and setting it also short-circuits native detection, breaking displays that would otherwise be
 * detected correctly. The value that drives layout and text is Compose's `LocalDensity`, applied
 * inside `Window` and paired with a pre-multiplied `WindowState.size` (CMP-6857).
 *
 * Sources, most authoritative first:
 *  1. `GDK_SCALE` x `GDK_DPI_SCALE` — what GTK clients are handed, so we agree with the rest of
 *     the desktop. Static for the process lifetime, so it is only a starting value.
 *  2. mutter's `monitors.xml` — re-read on every poll, which is what makes a scale change apply
 *     without a restart.
 *  3. Nothing usable: leave Compose's own density alone rather than forcing 1.0.
 *
 * Only Linux needs this. macOS and Windows already report HiDPI correctly, and forcing a value
 * there would override a correct native density.
 */
internal object DesktopScale {
    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 5f
    private const val POLL_INTERVAL_MS = 2_000L

    /**
     * Scale to force, or `null` to keep whatever Compose chose. `null` is the honest default: a
     * missing signal means "unknown", not "unscaled", and treating it as 1.0 is what made an
     * earlier version of this file wrong.
     */
    private val forced = MutableStateFlow<Float?>(null)

    val forcedScale: StateFlow<Float?> = forced.asStateFlow()

    /** Process-lifetime scope: the watcher must outlive whatever composition started it. */
    private val watcherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Scale in effect, for logging and for sizing decisions outside composition. */
    val factor: Float? get() = forced.value

    /**
     * Scale AWT applies to every coordinate it is given: `window.setSize(w)` covers `w * awtScale`
     * device pixels, and Compose sizes windows from the very same transform.
     *
     * This is the other half of [contentDensity] and the reason a forced scale needs a
     * pre-multiplied window size at all. It is 1.0 on the X11 setups this file exists for, but it
     * is read from the live `GraphicsConfiguration` rather than assumed, so a display that *does*
     * report a transform is not double-scaled.
     */
    val awtScale: Float by lazy {
        runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice
                .defaultConfiguration
                .defaultTransform
                .scaleX
                .toFloat()
        }.getOrDefault(1f).takeIf { it.isFinite() && it > 0f } ?: 1f
    }

    /**
     * Density the app's content is laid out and measured at: the detected desktop scale where there
     * is one, otherwise whatever Compose derived from [awtScale].
     *
     * Every place that has to turn a measured or `dp` size into an AWT call must divide by
     * [awtScale], never by this — the two only differ on exactly the setups that need fixing.
     */
    val contentDensity: Float get() = factor ?: awtScale

    /**
     * How much larger a window must be than its `dp` size for the content to fit, i.e.
     * [contentDensity] / [awtScale]. Compose sizes the AWT window from `WindowState.size` using
     * [awtScale] while the content is laid out at [contentDensity], so this factor reconciles them.
     */
    val sizeRatio: Float get() = contentDensity / awtScale

    fun isSupportedPlatform(): Boolean = isLinuxOs()

    /**
     * Watch the desktop's scale so a change takes effect without a restart. Cheap: the only real
     * work is a stat plus a small read of a config file, and it stops when the process exits.
     *
     * Deliberately callable *before* the first composition rather than from a `LaunchedEffect`.
     * The first composition is where the main window reads its remembered size, and doing the
     * first detection one frame later meant that size was computed against a null scale — the
     * window then opened at the wrong size and nothing corrected it, because a remembered
     * `WindowState` keeps whatever it was constructed with.
     */
    fun startWatching(scope: CoroutineScope = watcherScope) {
        if (!isLinuxOs()) return
        forced.value = detect()
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val next = detect()
                val previous = forced.value
                // Only react to real changes: a desktop that legitimately sits at 1.0 must not be
                // overridden by the platform default, and log spam on every poll helps nobody.
                if (previous == null && next == null) continue
                if (next == null) {
                    Logger.i("DesktopScale", "scale signal lost; reverting to Compose default")
                    forced.value = null
                } else if (previous == null || abs(next - previous) > 0.01f) {
                    Logger.i("DesktopScale", "desktop scale changed -> $next")
                    forced.value = next
                }
            }
        }
    }

    private fun detect(): Float? {
        val candidates = listOf(
            "env" to envScale(),
            "monitors.xml" to monitorsXmlScale(),
        )
        // First usable value wins, in declaration order. Values within [MIN_SCALE, MAX_SCALE] are
        // considered; anything else means the file was rewritten by something we do not understand.
        val best = candidates.firstOrNull { (_, value) ->
            value != null && value.isFinite() && value in MIN_SCALE..MAX_SCALE
        }
        val rendered = candidates.joinToString(", ") { (source, value) ->
            "$source=${value?.let { trim(it) } ?: "n/a"}"
        }
        if (best == null) {
            Logger.d("DesktopScale", "no usable scale signal ($rendered)")
            return null
        }
        val value = best.second ?: return null
        // This runs on every poll, so keep it at debug; the watch loop logs only real changes.
        Logger.d("DesktopScale", "scale=${trim(value)} from ${best.first} (candidates: $rendered)")
        return value
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

    /**
     * `<scale>` of the primary logical monitor in mutter's monitors.xml, which it rewrites whenever
     * the user changes scaling — that rewrite is the signal the poll keys off.
     */
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
                // <scale> is written *before* <primary>, so extract from the whole
                // <logicalmonitor> block rather than assuming a tag order.
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