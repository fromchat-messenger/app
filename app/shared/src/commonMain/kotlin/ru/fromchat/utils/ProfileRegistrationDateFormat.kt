package ru.fromchat.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.month_name_apr
import ru.fromchat.month_name_aug
import ru.fromchat.month_name_dec
import ru.fromchat.month_name_feb
import ru.fromchat.month_name_jan
import ru.fromchat.month_name_jul
import ru.fromchat.month_name_jun
import ru.fromchat.month_name_mar
import ru.fromchat.month_name_may
import ru.fromchat.month_name_nov
import ru.fromchat.month_name_oct
import ru.fromchat.month_name_sep
import ru.fromchat.profile_registration_date
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private fun formatFromXmlTemplate(template: String, vararg args: Any): String {
    var result = template
    args.forEachIndexed { index, arg ->
        val n = index + 1
        val text = arg.toString()
        result = result.replace($$"%$${n}$s", text).replace($$"%$${n}$d", text)
    }
    return result
}

data class RegistrationDateFormatStrings(
    val template: String,
    val monthName: (Int) -> String,
)

@Composable
fun rememberRegistrationDateFormatStrings(): RegistrationDateFormatStrings {
    val template = stringResource(Res.string.profile_registration_date)
    val jan = stringResource(Res.string.month_name_jan)
    val feb = stringResource(Res.string.month_name_feb)
    val mar = stringResource(Res.string.month_name_mar)
    val apr = stringResource(Res.string.month_name_apr)
    val may = stringResource(Res.string.month_name_may)
    val jun = stringResource(Res.string.month_name_jun)
    val jul = stringResource(Res.string.month_name_jul)
    val aug = stringResource(Res.string.month_name_aug)
    val sep = stringResource(Res.string.month_name_sep)
    val oct = stringResource(Res.string.month_name_oct)
    val nov = stringResource(Res.string.month_name_nov)
    val dec = stringResource(Res.string.month_name_dec)

    return remember(
        template, jan, feb, mar, apr, may, jun, jul, aug, sep, oct, nov, dec,
    ) {
        val months = listOf(jan, feb, mar, apr, may, jun, jul, aug, sep, oct, nov, dec)
        RegistrationDateFormatStrings(
            template = template,
            monthName = { monthNumber -> months.getOrElse(monthNumber - 1) { "" } },
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun parseRegistrationLocalDate(iso: String): LocalDate? {
    runCatching { Instant.parse(iso) }.getOrNull()?.let {
        return it.toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    runCatching { LocalDateTime.parse(iso).date }.getOrNull()?.let { return it }
    return runCatching { LocalDate.parse(iso.substringBefore('T')) }.getOrNull()
}

/** Formats an ISO registration timestamp as e.g. "13 june 2026" (localized). */
@OptIn(ExperimentalTime::class)
fun formatProfileRegistrationDate(
    createdAtIso: String?,
    strings: RegistrationDateFormatStrings,
): String? {
    val iso = createdAtIso?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val date = parseRegistrationLocalDate(iso) ?: return iso
    return formatFromXmlTemplate(
        strings.template,
        date.day,
        strings.monthName(date.month.number),
        date.year,
    )
}
