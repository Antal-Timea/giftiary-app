package app.gifty.domain.model

import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.util.UUID

class OccasionTest {

    @Test
    fun `birthday without a year is valid`() {
        occasion(day = 12, month = 5, year = null)
    }

    @Test
    fun `leap day is valid when the year is unknown`() {
        occasion(day = 29, month = 2, year = null)
    }

    @Test
    fun `leap day is rejected in a non-leap year`() {
        assertThrows(IllegalArgumentException::class.java) {
            occasion(day = 29, month = 2, year = 2025)
        }
    }

    @Test
    fun `impossible day or month is rejected`() {
        listOf(0 to 1, 32 to 1, 31 to 4, 1 to 0, 1 to 13).forEach { (day, month) ->
            assertThrows("$day/$month should be rejected", IllegalArgumentException::class.java) {
                occasion(day = day, month = month, year = null)
            }
        }
    }

    private fun occasion(day: Int, month: Int, year: Int?) = Occasion(
        id = UUID.randomUUID(),
        personId = UUID.randomUUID(),
        type = OccasionType.BIRTHDAY,
        title = null,
        day = day,
        month = month,
        year = year,
        repeatsYearly = true,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
