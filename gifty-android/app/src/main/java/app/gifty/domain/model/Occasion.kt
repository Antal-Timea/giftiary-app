package app.gifty.domain.model

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
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
        require(isValidDate(day, month, year)) {
            "Invalid occasion date: day=$day, month=$month, year=$year"
        }
    }

    private companion object {
        fun isValidDate(day: Int, month: Int, year: Int?): Boolean = try {
            if (year == null) MonthDay.of(month, day) else LocalDate.of(year, month, day)
            true
        } catch (_: DateTimeException) {
            false
        }
    }
}

enum class OccasionType {
    BIRTHDAY,
    NAME_DAY,
    ANNIVERSARY,
    CHRISTMAS,
    CUSTOM,
}
