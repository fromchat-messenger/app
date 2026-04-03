package ru.fromchat.ui.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Get gradient brush for own messages
 */
fun getMessageGradient(isDark: Boolean) = Brush.linearGradient(
    colors = if (isDark) {
        listOf(
            Color(0xFF9333EA),
            Color(0xFF6366F1),
            Color(0xFF2F68C5)
        )
    } else {
        listOf(
            Color(0xFFB794F6),
            Color(0xFF818CF8),
            Color(0xFF60A5FA)
        )
    },
    start = Offset(0f, 0f),
    end = Offset(1000f, 1000f)
)

/**
 * Get gradient brush for own messages
 */
fun getReplyMessageGradient(isDark: Boolean) = Brush.linearGradient(
    colors = if (isDark) {
        listOf(
            Color(0xFF5f11a6),
            Color(0xFF1418da),
            Color(0xFF1b3d73)
        )
    } else {
        listOf(
            Color(0xFF7836ee),
            Color(0xFF2034f3),
            Color(0xFF076eed)
        )
    },
    start = Offset(0f, 0f),
    end = Offset(1000f, 1000f)
)

/**
 * Generate a consistent gradient from a name for avatar fallback
 */
fun generateGradientFromName(name: String): Brush {
    val hash = name.hashCode()
    val r = abs(hash % 256)
    val g = abs((hash / 256) % 256)
    val b = abs((hash / 65536) % 256)
    
    // Create two colors based on hash for gradient
    return Brush.linearGradient(
        colors = listOf(
            Color(
                red = (r + 100).coerceIn(0, 255) / 255f,
                green = (g + 100).coerceIn(0, 255) / 255f,
                blue = (b + 100).coerceIn(0, 255) / 255f
            ),
            Color(
                red = (r + 50).coerceIn(0, 255) / 255f,
                green = (g + 50).coerceIn(0, 255) / 255f,
                blue = (b + 50).coerceIn(0, 255) / 255f
            )
        ),
        start = Offset(0f, 0f),
        end = Offset(100f, 100f)
    )
}

/**
 * Get initials from display name (first 2 words, first letter of each)
 */
fun getInitials(displayName: String): String {
    val words = displayName.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> {
            val word = words[0]
            if (word.length >= 2) {
                word.take(2).uppercase()
            } else {
                (word + word).take(2).uppercase()
            }
        }
        else -> {
            words.take(2).joinToString("") { it.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
        }
    }
}


