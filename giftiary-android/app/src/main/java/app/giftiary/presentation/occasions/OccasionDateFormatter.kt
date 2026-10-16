package app.giftiary.presentation.occasions

import android.text.format.DateFormat
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats an occasion's date in the locale's own order and month names, e.g. "March 12" or
 * "12 March" in English and "március 12." in Hungarian. An unknown year is left out instead of
 * being substituted, by formatting a [MonthDay].
 */
object OccasionDateFormatter {

    fun format(day: Int, month: Int, year: Int?, locale: Locale): String =
        if (year == null) {
            formatter(MONTH_DAY_SKELETON, locale).format(MonthDay.of(month, day))
        } else {
            formatter(FULL_DATE_SKELETON, locale).format(LocalDate.of(year, month, day))
        }

    // ICU picks the locale's preferred pattern for these fields, including order and punctuation.
    private fun formatter(skeleton: String, locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    private const val MONTH_DAY_SKELETON = "MMMMd"
    private const val FULL_DATE_SKELETON = "yMMMMd"
}
