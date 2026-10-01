package app.gifty.domain.model

import java.util.Currency

/**
 * How many decimal places a currency's [Money.amountMinor] carries.
 *
 * ISO 4217 data is used except where everyday usage differs: forints are never subdivided in
 * practice, so 12 500 Ft is stored as 12500 rather than 1250000.
 */
object CurrencyMinorUnits {

    private val overrides = mapOf("HUF" to 0)

    fun fractionDigits(currencyCode: String): Int =
        overrides[currencyCode] ?: Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
}
