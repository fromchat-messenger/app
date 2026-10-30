package ru.fromchat.ui.components

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fromchat.utils.toPx
import org.jetbrains.compose.resources.Font
import ru.fromchat.Res
import ru.fromchat.montserrat_cyrillic
import ru.fromchat.montserrat_cyrillic_ext
import ru.fromchat.montserrat_latin
import ru.fromchat.montserrat_latin_ext
import ru.fromchat.montserrat_vietnamese
import kotlin.math.abs

val titleGradientStops = arrayOf(
    0f to Color(0xFF6366F1),
    0.2f to Color(0xFF3B82F6),
    0.4f to Color(0xFF9333EA),
    0.6f to Color(0xFFA855F7),
    0.8f to Color(0xFFD946EF),
    1f to Color(0xFFEC4899),
)

@Composable
fun BrandTitle(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var drawnTextWidth by remember { mutableFloatStateOf(0f) }
    val brush = remember(drawnTextWidth) {
        Brush.linearGradient(
            colorStops = titleGradientStops,
            start = Offset.Zero,
            end = Offset(if (drawnTextWidth > 1f) drawnTextWidth else 180.dp.toPx(density), 0f),
        )
    }

    Text(
        text = "FromChat",
        modifier = modifier.wrapContentWidth(align = Alignment.Start),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        onTextLayout = { layout ->
            val w = layout.size.width.toFloat()
            if (abs(w - drawnTextWidth) > 0.5f) {
                drawnTextWidth = w
            }
        },
        style = TextStyle(
            fontFamily = FontFamily(
                Font(Res.font.montserrat_latin, FontWeight.Bold, FontStyle.Normal),
                Font(Res.font.montserrat_latin_ext, FontWeight.Bold, FontStyle.Normal),
                Font(Res.font.montserrat_cyrillic, FontWeight.Bold, FontStyle.Normal),
                Font(Res.font.montserrat_cyrillic_ext, FontWeight.Bold, FontStyle.Normal),
                Font(Res.font.montserrat_vietnamese, FontWeight.Bold, FontStyle.Normal),
            ),
            fontWeight = FontWeight.SemiBold,
            fontSize = 29.sp,
            lineHeight = 34.sp,
            brush = brush,
            shadow = Shadow(
                color = Color(0x809333EA),
                offset = Offset.Zero,
                blurRadius = 20.dp.toPx(density),
            )
        )
    )
}
