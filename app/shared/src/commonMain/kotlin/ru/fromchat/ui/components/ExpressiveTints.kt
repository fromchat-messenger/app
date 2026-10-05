package ru.fromchat.ui.components

import androidx.compose.ui.graphics.Color

/**
 * Icon tile colors for the expressive list items — "light" tinted background
 * pairs with a matching darker "sub" (icon glyph) color. Mirrors the
 * settings-card reference: a soft colored chip behind a saturated icon.
 */
object ExpressiveTints {
    /** Soft blue tile, deep-blue glyph. */
    val LightBlue: Color = Color(0xFFE9F1FF)
    val Blue: Color = Color(0xFF3B82F6)

    /** Soft green tile, green glyph. */
    val LightGreen: Color = Color(0xFFE9F9EF)
    val Green: Color = Color(0xFF1F8A4C)

    /** Soft amber tile, amber glyph. */
    val LightYellow: Color = Color(0xFFFFF6E0)
    val Yellow: Color = Color(0xFFB26B00)

    /** Soft red tile, red glyph. */
    val LightRed: Color = Color(0xFFFEEBEC)
    val Red: Color = Color(0xFFE5484D)

    /** Soft violet tile, violet glyph. */
    val LightPurple: Color = Color(0xFFEFEAFE)
    val Purple: Color = Color(0xFF7C5CFC)

    /** Neutral surface tile (accent / default). */
    val NeutralTile: Color = Color(0xFF1C1C1C).copy(alpha = 0.06f)
}
