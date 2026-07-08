package ru.fromchat.ui.chat

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import ru.fromchat.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.typing_alone
import ru.fromchat.typing_many
import ru.fromchat.typing_single
import ru.fromchat.typing_two

@Composable
fun TypingIndicator(
    typingUsers: List<String>,
    showUsernames: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (typingUsers.isEmpty()) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypingDots()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatTypingText(typingUsers, showUsernames),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun formatTypingText(typingUsers: List<String>, showUsernames: Boolean): String {
    return when {
        typingUsers.isEmpty() -> ""
        !showUsernames && typingUsers.size == 1 ->
            stringResource(Res.string.typing_alone)
        typingUsers.size == 1 -> stringResource(Res.string.typing_single, typingUsers[0])
        typingUsers.size == 2 -> stringResource(Res.string.typing_two, typingUsers[0], typingUsers[1])
        else -> stringResource(
            Res.string.typing_many,
            typingUsers[0],
            typingUsers[1],
            typingUsers.size - 2
        )
    }
}

@Composable
private fun TypingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")
    val duration = 1000
    val initialScale = 1f
    val targetScale = 1.3f

    // Функция-хелпер для создания анимации с задержкой
    @Composable
    fun animateDotScale(delay: Int) = infiniteTransition.animateFloat(
        initialValue = initialScale,
        targetValue = initialScale, // Возвращаемся в начало
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = duration
                initialScale at delay // Начало подъема
                targetScale at delay + 200 // Пик
                initialScale at delay + 400 // Возврат
                initialScale at duration // Удержание до конца цикла
            }
        ),
        label = "dot_scale"
    )

    val dot1Scale by animateDotScale(delay = 0)
    val dot2Scale by animateDotScale(delay = 200)
    val dot3Scale by animateDotScale(delay = 400)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp) // Вместо Spacer
    ) {
        Dot(scale = dot1Scale, maxScale = targetScale)
        Dot(scale = dot2Scale, maxScale = targetScale)
        Dot(scale = dot3Scale, maxScale = targetScale)
    }
}

@Composable
@Suppress("SameParameterValue")
private fun Dot(maxScale: Float, scale: Float) {
    Box(
        modifier = Modifier.size(4.dp * maxScale),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(4.dp * scale)
                .height(4.dp * scale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {}
    }
}