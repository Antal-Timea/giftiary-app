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
        assertEquals(ParseResult.Valid(Money(12500, "HUF")), PriceFormat.parse("12500", "HUF"))
    }

    @Test
    fun `forints reject fractional input with either separator`() {
        assertEquals(ParseResult.TooPrecise, PriceFormat.parse("12.50", "HUF"))
        assertEquals(ParseResult.TooPrecise, PriceFormat.parse("12,50", "HUF"))
        // Trailing zeros carry no precision.
        assertEquals(ParseResult.Valid(Money(12500, "HUF")), PriceFormat.parse("12500,00", "HUF"))
    }

    @Test
    fun `euros accept a dot or a comma as decimal separator`() {
        assertEquals(ParseResult.Valid(Money(1250, "EUR")), PriceFormat.parse("12.50", "EUR"))
        assertEquals(ParseResult.Valid(Money(1250, "EUR")), PriceFormat.parse("12,50", "EUR"))
        assertEquals(ParseResult.Valid(Money(1250, "EUR")), PriceFormat.parse("12.5", "EUR"))
        assertEquals(ParseResult.Valid(Money(1200, "EUR")), PriceFormat.parse("12", "EUR"))
    }

    @Test
    fun `parsing does not depend on the device locale`() {
        val previous = Locale.getDefault()
        try {
            listOf(english, hungarian).forEach { locale ->
                Locale.setDefault(locale)
                assertEquals("$locale dot", ParseResult.Valid(Money(1250, "EUR")), PriceFormat.parse("12.50", "EUR"))
                assertEquals("$locale comma", ParseResult.Valid(Money(1250, "EUR")), PriceFormat.parse("12,50", "EUR"))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `grouped and mixed-separator input is rejected`() {
        listOf("1,234.50", "1.234,50", "12.5.0", "12,5,0", "12 500", "12 500").forEach { input ->
            assertEquals(input, ParseResult.Invalid, PriceFormat.parse(input, "EUR"))
        }
    }

    @Test
    fun `malformed input is rejected`() {
        listOf("abc", "1e3", ".", ",", "12.", ".5", "Ft", "12 Ft", "--5").forEach { input ->
            assertEquals(input, ParseResult.Invalid, PriceFormat.parse(input, "EUR"))
        }
        assertEquals(ParseResult.Invalid, PriceFormat.parse("99999999999999999999", "HUF"))
    }

    @Test
    fun `surrounding whitespace is ignored and blank input means no price`() {
        assertEquals(ParseResult.Valid(Money(12500, "HUF")), PriceFormat.parse("  12500 ", "HUF"))
        assertEquals(ParseResult.Valid(null), PriceFormat.parse("", "HUF"))
        assertEquals(ParseResult.Valid(null), PriceFormat.parse("   ", "HUF"))
    }

    @Test
    fun `negative amounts are rejected`() {
        assertEquals(ParseResult.Negative, PriceFormat.parse("-100", "HUF"))
        assertEquals(ParseResult.Negative, PriceFormat.parse("-12,50", "EUR"))
        assertEquals(ParseResult.Negative, PriceFormat.parse("−12.50", "EUR"))
    }

    @Test
    fun `more decimals than the currency has are rejected instead of rounded`() {
        assertEquals(ParseResult.TooPrecise, PriceFormat.parse("12.505", "EUR"))
        assertEquals(ParseResult.TooPrecise, PriceFormat.parse("12,505", "EUR"))
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
    fun `pre-filled input follows the locale and parses back to the same price`() {
        val euros = Money(1250, "EUR")
        assertEquals("12,50", PriceFormat.formatForInput(euros, hungarian))
        assertEquals("12.50", PriceFormat.formatForInput(euros, english))
        assertEquals(ParseResult.Valid(euros), PriceFormat.parse(PriceFormat.formatForInput(euros, hungarian), "EUR"))
        assertEquals(ParseResult.Valid(euros), PriceFormat.parse(PriceFormat.formatForInput(euros, english), "EUR"))
        assertEquals("12500", PriceFormat.formatForInput(Money(12500, "HUF"), english))
    }

    // Locales use different (non-breaking) spaces; their exact kind is not what is under test.
    private fun String.normalizeSpaces() = replace(' ', ' ').replace(' ', ' ')
}
