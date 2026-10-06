package ru.fromchat.ui.components

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import ru.fromchat.Res
import ru.fromchat.google_sans_rounded_regular

/**
 * Typography matching Vaffuru's expressive Material 3 look.
 *
 * Font: **Google Sans Rounded** (the `zE.ttf` shipped in the Vaffuru APK — OFL,
 * weight-class 500, 36k glyphs with full Latin + Cyrillic + Greek coverage).
 * Multiple weight instances are registered (300–900) so Compose picks the
 * closest available weight per role.
 *
 * Vaffuru's `s27.java` uses a single FontFamily of `google_sans_rounded_regular`
 * with 6 weight slots (300, 400, 500, 600, 700, 900) plus ROND=100 + wght
 * variable settings (no-op for a non-variable font, kept for intent).
 */
@Composable
fun googleSansFlexTypography(): Typography {
    val family = FontFamily(
        Font(Res.font.google_sans_rounded_regular, FontWeight(300)),
        Font(Res.font.google_sans_rounded_regular, FontWeight(400)),
        Font(Res.font.google_sans_rounded_regular, FontWeight(500)),
        Font(Res.font.google_sans_rounded_regular, FontWeight(600)),
        Font(Res.font.google_sans_rounded_regular, FontWeight(700)),
        Font(Res.font.google_sans_rounded_regular, FontWeight(900)),
    )

    return Typography(
        displayLarge = TextStyle(
            fontFamily = family,
            fontSize = 48.sp,
            fontWeight = FontWeight(700),
            lineHeight = 56.sp,
            letterSpacing = 0.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = family,
            fontSize = 36.sp,
            fontWeight = FontWeight(700),
            lineHeight = 44.sp,
            letterSpacing = 0.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = family,
            fontSize = 30.sp,
            fontWeight = FontWeight(400),
            lineHeight = 38.sp,
            letterSpacing = 0.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = family,
            fontSize = 32.sp,
            fontWeight = FontWeight(600),
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontSize = 28.sp,
            fontWeight = FontWeight(600),
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontSize = 24.sp,
            fontWeight = FontWeight(600),
            lineHeight = 32.sp,
            letterSpacing = 0.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontSize = 22.sp,
            fontWeight = FontWeight(400),
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontSize = 18.sp,
            fontWeight = FontWeight(500),
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontSize = 14.sp,
            fontWeight = FontWeight(500),
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontSize = 16.sp,
            fontWeight = FontWeight(400),
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontSize = 14.sp,
            fontWeight = FontWeight(400),
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontSize = 12.sp,
            fontWeight = FontWeight(400),
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontSize = 16.sp,
            fontWeight = FontWeight(500),
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontSize = 14.sp,
            fontWeight = FontWeight(500),
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontSize = 11.sp,
            fontWeight = FontWeight(500),
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )
}
