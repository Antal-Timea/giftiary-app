package app.gifty.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class UpcomingOccasionTest {

    private val today = LocalDate.of(2026, 5, 10)

    @Test
    fun `a yearly occasion falls on its day this year until it has passed, then next year`() {
        assertEquals(LocalDate.of(2026, 5, 10), next(day = 10, month = 5, onOrAfter = today))
        assertEquals(LocalDate.of(2026, 5, 11), next(day = 11, month = 5, onOrAfter = today))
        assertEquals(LocalDate.of(2027, 5, 9), next(day = 9, month = 5, onOrAfter = today))
        assertEquals(LocalDate.of(2027, 1, 1), next(day = 1, month = 1, onOrAfter = LocalDate.of(2026, 12, 31)))
        assertEquals(LocalDate.of(2026, 12, 31), next(day = 31, month = 12, onOrAfter = LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `a known year does not matter once it has passed, and the first occurrence is that date itself`() {
        assertEquals(LocalDate.of(2026, 6, 1), next(day = 1, month = 6, year = 1990, onOrAfter = today))
        assertEquals(LocalDate.of(2028, 3, 1), next(day = 1, month = 3, year = 2028, onOrAfter = today))
        assertEquals(LocalDate.of(2026, 5, 10), next(day = 10, month = 5, year = 2026, onOrAfter = today))
    }

    @Test
    fun `29 February is kept on 28 February in common years and on its own day in leap years`() {
        assertEquals(LocalDate.of(2027, 2, 28), next(day = 29, month = 2, onOrAfter = today))
        assertEquals(LocalDate.of(2027, 2, 28), next(day = 29, month = 2, year = 2000, onOrAfter = LocalDate.of(2027, 2, 28)))
        assertEquals(LocalDate.of(2028, 2, 29), next(day = 29, month = 2, onOrAfter = LocalDate.of(2027, 3, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), next(day = 29, month = 2, onOrAfter = LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun `a one-time occasion falls only on its own date`() {
        assertEquals(LocalDate.of(2026, 7, 4), next(day = 4, month = 7, year = 2026, repeats = false, onOrAfter = today))
        assertEquals(LocalDate.of(2026, 5, 10), next(day = 10, month = 5, year = 2026, repeats = false, onOrAfter = today))
        assertNull(next(day = 9, month = 5, year = 2026, repeats = false, onOrAfter = today))
        assertNull(next(day = 4, month = 7, year = 2025, repeats = false, onOrAfter = today))
    }

    @Test
    fun `a date that does not exist, or a one-time occasion without a year, never falls`() {
        assertNull(next(day = 31, month = 4, onOrAfter = today))
        assertNull(next(day = 0, month = 5, onOrAfter = today))
        assertNull(next(day = 1, month = 13, onOrAfter = today))
        assertNull(next(day = 1, month = 0, onOrAfter = today))
        assertNull(next(day = 29, month = 2, year = 2027, onOrAfter = today))
        assertNull(next(day = 1, month = 6, year = null, repeats = false, onOrAfter = today))
    }

    @Test
    fun `the age turned is only told for a birthday with a known year, and only if positive`() {
        val date = LocalDate.of(2026, 6, 1)
        assertEquals(36, turningAge(occasion(OccasionType.BIRTHDAY, year = 1990), date))
        assertEquals(1, turningAge(occasion(OccasionType.BIRTHDAY, year = 2025), date))
        assertNull(turningAge(occasion(OccasionType.BIRTHDAY, year = 2026), date))
        assertNull(turningAge(occasion(OccasionType.BIRTHDAY, year = null), date))
        assertNull(turningAge(occasion(OccasionType.ANNIVERSARY, year = 1990), date))
        assertNull(turningAge(occasion(OccasionType.NAME_DAY, year = 1990), date))
    }

    @Test
    fun `an occasion's next occurrence uses its own fields`() {
        val oneTime = occasion(OccasionType.CUSTOM, year = 2026, repeats = false, day = 1, month = 6)
        assertEquals(LocalDate.of(2026, 6, 1), oneTime.nextOccurrence(today))
        assertNull(oneTime.nextOccurrence(LocalDate.of(2026, 6, 2)))
    }

    private fun next(day: Int, month: Int, year: Int? = null, repeats: Boolean = true, onOrAfter: LocalDate) =
        nextOccurrence(day, month, year, repeats, onOrAfter)

    private fun occasion(type: OccasionType, year: Int?, repeats: Boolean = true, day: Int = 1, month: Int = 6) = Occasion(
        id = UUID.randomUUID(),
        personId = null,
        type = type,
        title = "Party".takeIf { type == OccasionType.CUSTOM },
        day = day,
        month = month,
        year = year,
        repeatsYearly = repeats,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
