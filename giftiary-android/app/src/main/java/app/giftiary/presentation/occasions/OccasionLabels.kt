package app.giftiary.presentation.occasions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.giftiary.R
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
@ReadOnlyComposable
fun occasionTypeLabel(type: OccasionType): String = stringResource(
    when (type) {
        OccasionType.BIRTHDAY -> R.string.occasion_type_birthday
        OccasionType.NAME_DAY -> R.string.occasion_type_name_day
        OccasionType.ANNIVERSARY -> R.string.occasion_type_anniversary
        OccasionType.CHRISTMAS -> R.string.occasion_type_christmas
        OccasionType.CUSTOM -> R.string.occasion_type_custom
    },
)

/** The user's own name for a custom occasion, otherwise its type. */
@Composable
@ReadOnlyComposable
fun occasionTitle(occasion: Occasion): String =
    occasion.title?.takeIf { occasion.type == OccasionType.CUSTOM } ?: occasionTypeLabel(occasion.type)

@Composable
@ReadOnlyComposable
fun occasionDate(occasion: Occasion): String =
    OccasionDateFormatter.format(occasion.day, occasion.month, occasion.year, currentLocale())

/** The day and month an occasion returns on each year, e.g. "October 12": a birth year is not repeated. */
@Composable
@ReadOnlyComposable
fun occasionDayMonth(occasion: Occasion): String =
    OccasionDateFormatter.format(occasion.day, occasion.month, year = null, currentLocale())

/** [occasionDayMonth] with the short month, for rows that must stay on one line: "Oct 12". */
@Composable
@ReadOnlyComposable
fun occasionShortDayMonth(day: Int, month: Int): String = OccasionDateFormatter.formatShort(day, month, currentLocale())

/** The day an occasion falls on next; the year is left out, as it is clear from how soon it is. */
@Composable
@ReadOnlyComposable
fun occurrenceDate(date: LocalDate): String =
    OccasionDateFormatter.format(date.dayOfMonth, date.monthValue, year = null, currentLocale())

/** Standalone month name for pickers, e.g. "March" or "március". */
@Composable
@ReadOnlyComposable
fun monthName(month: Int): String = Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, currentLocale())

@Composable
@ReadOnlyComposable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
