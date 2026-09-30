package app.gifty.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {

    @Test
    fun `accepts non-negative amount with ISO style currency code`() {
        val money = Money(amountMinor = 12990, currencyCode = "HUF")

        assertEquals(12990, money.amountMinor)
        assertEquals("HUF", money.currencyCode)
    }

    @Test
    fun `accepts zero amount`() {
        Money(amountMinor = 0, currencyCode = "EUR")
    }

    @Test
    fun `rejects negative amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(amountMinor = -1, currencyCode = "EUR")
        }
    }

    @Test
    fun `rejects currency codes that are not exactly three uppercase letters`() {
        listOf("", "EU", "EURO", "eur", "Eur", "E1R", " EU", "ÄBC").forEach { code ->
            assertThrows("'$code' should be rejected", IllegalArgumentException::class.java) {
                Money(amountMinor = 100, currencyCode = code)
            }
        }
    }
}
