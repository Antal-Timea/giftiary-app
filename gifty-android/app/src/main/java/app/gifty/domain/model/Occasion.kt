package app.gifty.domain.model

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year
import java.util.UUID

/**
 * A calendar occasion. The year is optional because it is often unknown,
 * e.g. a friend's birthday without their birth year.
 */
data class Occasion(
    val id: UUID,
    val personId: UUID?,
    val type: OccasionType,
    val title: String?,
    val day: Int,
    val month: Int,
    val year: Int?,
    val repeatsYearly: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(dateProblem(day, month, year) == null) {
            "Invalid occasion date: day=$day, month=$month, year=$year"
        }
    }

    companion object {

        /** Returns why the date does not exist on the calendar, or null if it is valid. */
        fun dateProblem(day: Int, month: Int, year: Int?): OccasionDateProblem? {
            if (month !in 1..12) return OccasionDateProblem.INVALID_MONTH
            if (year != null && year !in Year.MIN_VALUE..Year.MAX_VALUE) return OccasionDateProblem.INVALID_YEAR
            try {
                MonthDay.of(month, day)
            } catch (_: DateTimeException) {
                return OccasionDateProblem.DAY_NOT_IN_MONTH
            }
            if (year != null) {
                try {
                    LocalDate.of(year, month, day)
                } catch (_: DateTimeException) {
                    // MonthDay accepted it, so only 29 February in a common year can fail here.
                    return OccasionDateProblem.NOT_A_LEAP_YEAR
                }
            }
            return null
        }
    }
}

enum class OccasionDateProblem {
    INVALID_MONTH,
    INVALID_YEAR,
    DAY_NOT_IN_MONTH,
    NOT_A_LEAP_YEAR,
}

/**
 * [alwaysRepeatsYearly] types recur by definition. Anniversaries usually recur but may mark a
 * single date, so like custom occasions they leave the choice to the user.
 */
enum class OccasionType(val alwaysRepeatsYearly: Boolean) {
    BIRTHDAY(alwaysRepeatsYearly = true),
    NAME_DAY(alwaysRepeatsYearly = true),
    ANNIVERSARY(alwaysRepeatsYearly = false),
    CHRISTMAS(alwaysRepeatsYearly = true),
    CUSTOM(alwaysRepeatsYearly = false),
}
