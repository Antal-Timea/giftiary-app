package app.gifty.domain.usecase

import app.gifty.domain.model.OccasionType
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.repository.OccasionEntry
import app.gifty.testing.occasion
import app.gifty.testing.person
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The reminder's choice of occasions, from the same calculation Coming up uses. */
class UpcomingOccasionsTest {

    private val anna = person("Anna")
    private val today = LocalDate.of(2026, 5, 10)

    @Test
    fun `a reminder is about occasions today and exactly a week later, nothing in between or after`() {
        val entries = (0..10).map { days -> entry(yearlyIn(days)) }

        assertEquals(listOf(0, 7), dueReminders(entries, today).map { it.daysUntil })
    }

    @Test
    fun `29 February counts on 28 February in a common year, today and a week ahead`() {
        val leapDay = entry(OccasionDetails(OccasionType.BIRTHDAY, null, 29, 2, null, true))

        assertEquals(listOf(LocalDate.of(2027, 2, 28)), dueReminders(listOf(leapDay), LocalDate.of(2027, 2, 28)).map { it.date })
        assertEquals(listOf(LocalDate.of(2027, 2, 28)), dueReminders(listOf(leapDay), LocalDate.of(2027, 2, 21)).map { it.date })
    }

    @Test
    fun `occasions of the user's own are included, a past one-time occasion is not`() {
        val christmas = OccasionEntry(occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 17, 5, null, true)), null)
        val pastTrip = OccasionEntry(occasion(null, OccasionDetails(OccasionType.CUSTOM, "Trip", 17, 5, 2025, false)), null)

        assertEquals(listOf(christmas.occasion), dueReminders(listOf(christmas, pastTrip), today).map { it.occasion })
    }

    @Test
    fun `the shared calculation keeps the Coming up window and order`() {
        val entries = listOf(entry(yearlyIn(61)), entry(yearlyIn(60)), entry(yearlyIn(0)))

        assertEquals(listOf(0, 60), upcomingOccasions(entries, today, windowDays = 60).map { it.daysUntil })
    }

    private fun yearlyIn(days: Int): OccasionDetails {
        val date = today.plusDays(days.toLong())
        return OccasionDetails(OccasionType.BIRTHDAY, null, date.dayOfMonth, date.monthValue, null, true)
    }

    private fun entry(details: OccasionDetails) = OccasionEntry(occasion(anna.id, details), anna)
}
