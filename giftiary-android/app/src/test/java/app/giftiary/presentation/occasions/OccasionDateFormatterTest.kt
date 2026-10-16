package app.giftiary.presentation.occasions

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** Locales are passed explicitly, so results do not depend on the machine running the tests. */
@RunWith(RobolectricTestRunner::class)
class OccasionDateFormatterTest {

    private val english = Locale.forLanguageTag("en-US")
    private val british = Locale.forLanguageTag("en-GB")
    private val hungarian = Locale.forLanguageTag("hu-HU")

    @Test
    fun `date without a year shows only month and day in the locale's order`() {
        assertEquals("March 12", OccasionDateFormatter.format(12, 3, null, english))
        assertEquals("12 March", OccasionDateFormatter.format(12, 3, null, british))
        assertEquals("március 12.", OccasionDateFormatter.format(12, 3, null, hungarian))
    }

    @Test
    fun `date with a year includes it`() {
        assertEquals("November 4, 1988", OccasionDateFormatter.format(4, 11, 1988, english))
        assertEquals("4 November 1988", OccasionDateFormatter.format(4, 11, 1988, british))
        assertEquals("1988. november 4.", OccasionDateFormatter.format(4, 11, 1988, hungarian))
    }

    @Test
    fun `leap day without a year is formatted without inventing a year`() {
        assertEquals("February 29", OccasionDateFormatter.format(29, 2, null, english))
        assertEquals("február 29.", OccasionDateFormatter.format(29, 2, null, hungarian))
    }
}
