package ru.fromchat.utils

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val monthShortEn = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/**
 * Returns a readable last-seen line in local time (24h clock), avoiding raw ISO dates.
 */
@OptIn(ExperimentalTime::class)
fun formatLastSeen(online: Boolean, lastSeenIso: String?): String {
    if (online) return "Online"
    val iso = lastSeenIso ?: return ""

    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return "Last seen recently"

    val timeZone = TimeZone.currentSystemDefault()
    val lastLocal = instant.toLocalDateTime(timeZone)
    val nowDate = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
        .toLocalDateTime(timeZone).date
    val lastDate = lastLocal.date

    val hour = lastLocal.hour.toString().padStart(2, '0')
    val minute = lastLocal.minute.toString().padStart(2, '0')
    val timePart = "$hour:$minute"

    val yesterday = nowDate.minus(DatePeriod(days = 1))
    val daysBetween = nowDate.toEpochDays() - lastDate.toEpochDays()

    return when {
        lastDate == nowDate -> "Last seen today at $timePart"
        lastDate == yesterday -> "Last seen yesterday at $timePart"
        daysBetween in 2..6 -> {
            val label = lastDate.dayOfWeek.name
                .lowercase()
                .split("_")
                .joinToString(" ") { word ->
                    word.replaceFirstChar { c -> c.titlecase() }
                }
            "Last seen $label at $timePart"
        }
        lastDate.year == nowDate.year -> {
            val mon = monthShortEn.getOrElse(lastDate.monthNumber - 1) { "" }
            "Last seen ${lastDate.dayOfMonth} $mon at $timePart"
        }
        else -> {
            val mon = monthShortEn.getOrElse(lastDate.monthNumber - 1) { "" }
            "Last seen ${lastDate.dayOfMonth} $mon ${lastDate.year} at $timePart"
        }
    }
}

