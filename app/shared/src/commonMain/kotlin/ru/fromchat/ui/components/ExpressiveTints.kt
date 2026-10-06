package ru.fromchat.ui.components

import androidx.compose.ui.graphics.Color

/**
 * A paired icon-tile color preset for the expressive list items / nav bar: a
 * "background" fill behind a matching "foreground" (glyph) color. Each preset is
 * a self-contained, reusable chip color extracted from the reference design.
 */
class IconPreset(
    val background: Color,
    val foreground: Color,
)

/**
 * The fixed set of [IconPreset]s used by the settings list and the floating nav
 * bar. Values are pulled directly from the Vaffuru reference (bg / fg pairs).
 */
object ExpressiveTints {
    /** Light lavender chip, deep-violet glyph. */
    val Purple = IconPreset(Color(0xFFB89CD1), Color(0xFF5229A4))

    /** Deep violet chip, magenta glyph. */
    val Rose = IconPreset(Color(0xFFD492BF), Color(0xFF8C0052))

    /** Gold chip, brown glyph. */
    val Yellow = IconPreset(Color(0xFFD29E0A), Color(0xFF693801))

    /** Warm terracotta chip, dark-brown glyph. */
    val Orange = IconPreset(Color(0xFFD39B71), Color(0xFF783707))

    /** Green chip, deep-green glyph. */
    val Green = IconPreset(Color(0xFF70B576), Color(0xFF00512B))

    /** Neutral surface tile (accent / default). */
    val NeutralTile: Color = Color(0xFF1C1C1C).copy(alpha = 0.06f)
}
