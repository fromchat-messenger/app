package ru.fromchat.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ru.fromchat.config.ServerConfig
import ru.fromchat.ui.chat.components.generateGradientFromName
import ru.fromchat.ui.chat.components.getInitials

@Composable
fun Avatar(
    profilePictureUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    isDeletedUser: Boolean = false,
    userId: Int? = null,
) {
    if (isDeletedUser) {
        val gradientSeed = userId?.toString() ?: displayName
        val gradient = remember(gradientSeed) { generateGradientFromName(gradientSeed) }
        Box(
            modifier = modifier.clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = gradient,
                    radius = radius,
                    center = center,
                )
            }
            Icon(
                imageVector = Icons.Outlined.PersonOff,
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(0.55f),
                tint = Color.White,
            )
        }
        return
    }

    var imageLoadFailed by remember { mutableStateOf(false) }

    val gradient = remember(displayName) { generateGradientFromName(displayName) }
    val initials = remember(displayName) { getInitials(displayName) }
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (profilePictureUrl != null && !imageLoadFailed) {
            val fullUrl = if (profilePictureUrl.startsWith("http")) {
                profilePictureUrl
            } else {
                "${ServerConfig.apiBaseUrl}$profilePictureUrl"
            }

            AsyncImage(
                model = fullUrl,
                contentDescription = displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                onError = { imageLoadFailed = true },
                onSuccess = { imageLoadFailed = false }
            )
        }

        if (imageLoadFailed || profilePictureUrl == null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = gradient,
                    radius = radius,
                    center = center
                )

                if (initials.isNotBlank()) {
                    val fontPx = radius * 0.7f
                    val fontSp = (fontPx / density).sp

                    val textLayout = textMeasurer.measure(
                        text = initials,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = fontSp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    val textWidth = textLayout.size.width.toFloat()
                    val textHeight = textLayout.size.height.toFloat()
                    val topLeft = Offset(
                        x = (size.width - textWidth) / 2f,
                        y = (size.height - textHeight) / 2f
                    )

                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = topLeft
                    )
                }
            }
        }
    }
}
