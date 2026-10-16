package app.giftiary.data.local.converter

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TimeConvertersTest {

    @Test
    fun `instant is stored as epoch millis and loses sub-millisecond precision`() {
        val instant = Instant.parse("2026-05-12T10:15:30.123456789Z")

        val stored = TimeConverters.fromInstant(instant)

        assertEquals(instant.toEpochMilli(), stored)
        assertEquals(Instant.parse("2026-05-12T10:15:30.123Z"), TimeConverters.toInstant(stored))
    }

    @Test
    fun `local date is stored as sortable ISO text`() {
        val date = LocalDate.of(2026, 5, 2)

        val stored = TimeConverters.fromLocalDate(date)

        assertEquals("2026-05-02", stored)
        assertEquals(date, TimeConverters.toLocalDate(stored))
    }
}
