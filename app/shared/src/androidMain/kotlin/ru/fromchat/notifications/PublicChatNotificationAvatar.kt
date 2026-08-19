package ru.fromchat.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs

/**
 * Builds a circular initials avatar matching the in-app public-chat row (title initials +
 * name-hash gradient), for use as a notification large icon.
 */
internal object PublicChatNotificationAvatar {
    private const val SIZE_PX = 192

    fun create(title: String): Bitmap {
        val seed = title.ifBlank { "FromChat" }
        val hash = seed.hashCode()
        val r = abs(hash % 256)
        val g = abs((hash / 256) % 256)
        val b = abs((hash / 65536) % 256)
        val colorStart = android.graphics.Color.rgb(
            (r + 100).coerceIn(0, 255),
            (g + 100).coerceIn(0, 255),
            (b + 100).coerceIn(0, 255),
        )
        val colorEnd = android.graphics.Color.rgb(
            (r + 50).coerceIn(0, 255),
            (g + 50).coerceIn(0, 255),
            (b + 50).coerceIn(0, 255),
        )

        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                SIZE_PX.toFloat(),
                SIZE_PX.toFloat(),
                colorStart,
                colorEnd,
                Shader.TileMode.CLAMP,
            )
        }
        val radius = SIZE_PX / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        val initials = initialsFrom(seed)
        if (initials.isNotBlank()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = radius * 0.7f
            }
            val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(initials, radius, textY, textPaint)
        }
        return bitmap
    }

    private fun initialsFrom(displayName: String): String {
        val words = displayName.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> ""
            words.size == 1 -> {
                val word = words[0]
                if (word.length >= 2) word.take(2).uppercase() else (word + word).take(2).uppercase()
            }
            else -> words.take(2).joinToString("") {
                it.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
            }
        }
    }
}
