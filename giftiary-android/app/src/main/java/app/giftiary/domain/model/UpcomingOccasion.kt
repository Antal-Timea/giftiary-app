package app.giftiary.domain.model

import java.time.LocalDate
import java.time.Month
import java.time.Year

/** An occasion's next date, as seen from a given day. */
data class UpcomingOccasion(
    val occasion: Occasion,
    val person: Person?,
    val date: LocalDate,
    val daysUntil: Int,
    /** Only for a birthday with a known birth year. */
    val turningAge: Int?,
)

/** See [nextOccurrence]. */
fun Occasion.nextOccurrence(onOrAfter: LocalDate): LocalDate? =
    nextOccurrence(day, month, year, repeatsYearly, onOrAfter)

/**
 * The first date on or after [onOrAfter] the occasion falls on, or null if it never will again.
 *
 * A yearly occasion falls on its day every year from its year on, if the year is known. In a
 * common year, 29 February is kept on 28 February so the occasion is not skipped. A one-time
 * occasion falls only on its own date, so it needs a year.
 *
 * Takes the stored fields rather than an [Occasion] so that a date that does not exist on the
 * calendar yields null instead of an exception.
 */
fun nextOccurrence(day: Int, month: Int, year: Int?, repeatsYearly: Boolean, onOrAfter: LocalDate): LocalDate? {
    if (Occasion.dateProblem(day, month, year) != null) return null
    if (!repeatsYearly) {
        return year?.let { LocalDate.of(it, month, day) }?.takeIf { !it.isBefore(onOrAfter) }
    }
    val firstYear = maxOf(onOrAfter.year, year ?: Int.MIN_VALUE)
    val thisYear = dateIn(firstYear, month, day)
    if (!thisYear.isBefore(onOrAfter)) return thisYear
    if (firstYear == Year.MAX_VALUE) return null
    return dateIn(firstYear + 1, month, day)
}

/** The age a person turns on [date], for a birthday with a known birth year; null otherwise. */
fun turningAge(occasion: Occasion, date: LocalDate): Int? {
    if (occasion.type != OccasionType.BIRTHDAY) return null
    val birthYear = occasion.year ?: return null
    return (date.year - birthYear).takeIf { it > 0 }
}

private fun dateIn(year: Int, month: Int, day: Int): LocalDate =
    if (month == Month.FEBRUARY.value && day == 29 && !Year.isLeap(year.toLong())) {
        LocalDate.of(year, month, 28)
    } else {
        LocalDate.of(year, month, day)
    }
