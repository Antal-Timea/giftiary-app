package app.gifty.presentation.gifts

import app.gifty.domain.model.Money
import app.gifty.presentation.gifts.PriceFormat.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PriceFormatTest {

    private val english = Locale.forLanguageTag("en-US")
    private val hungarian = Locale.forLanguageTag("hu-HU")

    @Test
    fun `forints have no minor unit`() {
        assertEquals(ParseResult.Valid(Money(12500, "HUF")), PriceFormat.parse("12500", "HUF", hungarian))
        assertEquals(ParseResult.Valid(Money(12500, "HUF")), PriceFormat.parse("12 500", "HUF", hungarian))
        assertEquals(ParseResult.Valid(Money(12500, "HUF")), PriceFormat.parse("12,500", "HUF", english))
    }

    @Test
    fun `euros are stored in cents`() {
        assertEquals(ParseResult.Valid(Money(1250, "EUR")), PriceFormat.parse("12.50", "EUR", english))
        assertEquals(ParseResult.Valid(Money(1250, "EUR")), PriceFormat.parse("12.5", "EUR", english))
        assertEquals(ParseResult.Valid(Money(1200, "EUR")), PriceFormat.parse("12", "EUR", english))
    }

    @Test
    fun `a decimal comma is accepted where the locale uses it, and a dot always is`() {
        assertEquals(ParseResult.Valid(Money(1250, "EUR")), PriceFormat.parse("12,50", "EUR", hungarian))
        assertEquals(ParseResult.Valid(Money(1250, "EUR")), PriceFormat.parse("12.50", "EUR", hungarian))
    }

    @Test
    fun `empty input means no price`() {
        assertEquals(ParseResult.Valid(null), PriceFormat.parse("  ", "HUF", hungarian))
    }

    @Test
    fun `negative amounts are rejected`() {
        assertEquals(ParseResult.Negative, PriceFormat.parse("-100", "HUF", hungarian))
    }

    @Test
    fun `malformed input is rejected`() {
        listOf("abc", "12.5.0", "1e3", ".", "12..5", "Ft").forEach { input ->
            assertEquals(input, ParseResult.Invalid, PriceFormat.parse(input, "EUR", english))
        }
        assertEquals(ParseResult.Invalid, PriceFormat.parse("99999999999999999999", "HUF", english))
    }

    @Test
    fun `more decimals than the currency has are rejected instead of rounded`() {
        assertEquals(ParseResult.TooPrecise, PriceFormat.parse("12.505", "EUR", english))
        assertEquals(ParseResult.TooPrecise, PriceFormat.parse("12500,5", "HUF", hungarian))
        // Trailing zeros carry no extra precision.
        assertEquals(ParseResult.Valid(Money(12500, "HUF")), PriceFormat.parse("12500,00", "HUF", hungarian))
    }

    @Test
    fun `display follows the locale`() {
        assertEquals("12 500 Ft", PriceFormat.format(Money(12500, "HUF"), hungarian).normalizeSpaces())
        assertEquals("12,50 EUR", PriceFormat.format(Money(1250, "EUR"), hungarian).normalizeSpaces())
        assertEquals("€12.50", PriceFormat.format(Money(1250, "EUR"), english))
        // Whether a space follows the code differs between the JVM's and Android's locale data.
        val englishForints = PriceFormat.format(Money(12500, "HUF"), english)
        assertTrue(englishForints, englishForints.startsWith("HUF") && englishForints.endsWith("12,500"))
    }

    @Test
    fun `input formatting round-trips through parsing`() {
        val euros = Money(1250, "EUR")
        assertEquals("12,50", PriceFormat.formatForInput(euros, hungarian))
        assertEquals(ParseResult.Valid(euros), PriceFormat.parse(PriceFormat.formatForInput(euros, hungarian), "EUR", hungarian))
        assertEquals("12500", PriceFormat.formatForInput(Money(12500, "HUF"), english))
    }

    // Locales use different (non-breaking) spaces; their exact kind is not what is under test.
    private fun String.normalizeSpaces() = replace(' ', ' ').replace(' ', ' ')
}
