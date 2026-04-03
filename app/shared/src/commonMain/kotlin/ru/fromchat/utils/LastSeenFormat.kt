package ru.fromchat.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private fun formatFromXmlTemplate(template: String, vararg args: Any): String {
    var result = template
    args.forEachIndexed { index, arg ->
        val n = index + 1
        val text = arg.toString()
        result = result.replace("%${n}\$s", text).replace("%${n}\$d", text)
    }
    return result
}

data class LastSeenFormatStrings(
    val online: String,
    val recently: String,
    val todayAt: String,
    val yesterdayAt: String,
    val weekdayAt: String,
    val dateThisYear: String,
    val dateFull: String,
    val weekdayLabel: (DayOfWeek) -> String,
    val monthShort: (Int) -> String,
)

@Composable
fun rememberLastSeenFormatStrings(): LastSeenFormatStrings {
    val online = stringResource(Res.string.presence_online)
    val recently = stringResource(Res.string.presence_recently)
    val todayAt = stringResource(Res.string.presence_today_at)
    val yesterdayAt = stringResource(Res.string.presence_yesterday_at)
    val weekdayAt = stringResource(Res.string.presence_weekday_at)
    val dateThisYear = stringResource(Res.string.presence_date_this_year)
    val dateFull = stringResource(Res.string.presence_date_full)
    val mon = stringResource(Res.string.weekday_mon)
    val tue = stringResource(Res.string.weekday_tue)
    val wed = stringResource(Res.string.weekday_wed)
    val thu = stringResource(Res.string.weekday_thu)
    val fri = stringResource(Res.string.weekday_fri)
    val sat = stringResource(Res.string.weekday_sat)
    val sun = stringResource(Res.string.weekday_sun)
    val mJan = stringResource(Res.string.month_jan)
    val mFeb = stringResource(Res.string.month_feb)
    val mMar = stringResource(Res.string.month_mar)
    val mApr = stringResource(Res.string.month_apr)
    val mMay = stringResource(Res.string.month_may)
    val mJun = stringResource(Res.string.month_jun)
    val mJul = stringResource(Res.string.month_jul)
    val mAug = stringResource(Res.string.month_aug)
    val mSep = stringResource(Res.string.month_sep)
    val mOct = stringResource(Res.string.month_oct)
    val mNov = stringResource(Res.string.month_nov)
    val mDec = stringResource(Res.string.month_dec)
    return remember(
        online, recently, todayAt, yesterdayAt, weekdayAt, dateThisYear, dateFull,
        mon, tue, wed, thu, fri, sat, sun,
        mJan, mFeb, mMar, mApr, mMay, mJun, mJul, mAug, mSep, mOct, mNov, mDec
    ) {
        val months = listOf(mJan, mFeb, mMar, mApr, mMay, mJun, mJul, mAug, mSep, mOct, mNov, mDec)
        LastSeenFormatStrings(
            online = online,
            recently = recently,
            todayAt = todayAt,
            yesterdayAt = yesterdayAt,
            weekdayAt = weekdayAt,
            dateThisYear = dateThisYear,
            dateFull = dateFull,
            weekdayLabel = { d ->
                when (d) {
                    DayOfWeek.MONDAY -> mon
                    DayOfWeek.TUESDAY -> tue
                    DayOfWeek.WEDNESDAY -> wed
                    DayOfWeek.THURSDAY -> thu
                    DayOfWeek.FRIDAY -> fri
                    DayOfWeek.SATURDAY -> sat
                    DayOfWeek.SUNDAY -> sun
                }
            },
            monthShort = { monthNumber -> months.getOrElse(monthNumber - 1) { "" } },
        )
    }
}

/**
 * Readable last-seen line in local time (24h), using strings from [rememberLastSeenFormatStrings].
 */
@OptIn(ExperimentalTime::class)
fun formatLastSeen(online: Boolean, lastSeenIso: String?, s: LastSeenFormatStrings): String {
    if (online) return s.online
    val iso = lastSeenIso ?: return ""
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return s.recently

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
        lastDate == nowDate -> formatFromXmlTemplate(s.todayAt, timePart)
        lastDate == yesterday -> formatFromXmlTemplate(s.yesterdayAt, timePart)
        daysBetween in 2..6 -> {
            val label = s.weekdayLabel(lastLocal.dayOfWeek)
            formatFromXmlTemplate(s.weekdayAt, label, timePart)
        }
        lastDate.year == nowDate.year -> {
            val mon = s.monthShort(lastDate.monthNumber)
            formatFromXmlTemplate(s.dateThisYear, lastDate.dayOfMonth, mon, timePart)
        }
        else -> {
            val mon = s.monthShort(lastDate.monthNumber)
            formatFromXmlTemplate(s.dateFull, lastDate.dayOfMonth, mon, lastDate.year, timePart)
        }
    }
}
