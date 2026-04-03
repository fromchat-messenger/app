package ru.fromchat.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import ru.fromchat.Res
import ru.fromchat.google_sans_bold
import ru.fromchat.google_sans_bold_italic
import ru.fromchat.google_sans_italic
import ru.fromchat.google_sans_medium
import ru.fromchat.google_sans_medium_italic
import ru.fromchat.google_sans_regular
import ru.fromchat.google_sans_semibold
import ru.fromchat.google_sans_semibold_italic

/**
 * Google Sans (static TTFs from Downloads bundle) for app-wide [MaterialTheme.typography].
 * The chats header brand title keeps its own Montserrat stack in [ru.fromchat.ui.branding.FromChatBrandTitle].
 */
@Composable
fun googleSansFontFamily(): FontFamily = FontFamily(
    Font(Res.font.google_sans_regular, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.google_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.google_sans_medium, FontWeight.Medium, FontStyle.Normal),
    Font(Res.font.google_sans_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(Res.font.google_sans_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(Res.font.google_sans_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(Res.font.google_sans_bold, FontWeight.Bold, FontStyle.Normal),
    Font(Res.font.google_sans_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

@Composable
fun googleSansMaterialTypography(): Typography {
    val family = googleSansFontFamily()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}
