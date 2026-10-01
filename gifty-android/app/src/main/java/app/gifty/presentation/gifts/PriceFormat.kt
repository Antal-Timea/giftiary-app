package app.gifty.presentation.gifts

import app.gifty.domain.model.CurrencyMinorUnits
import app.gifty.domain.model.Money
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Converts between what people type or read and [Money], without floating point.
 *
 * Input accepts the locale's decimal separator and, since many keypads only offer it, a dot.
 * Whitespace and the locale's grouping separator are ignored. Input with more decimals than the
 * currency has is rejected rather than rounded, so a price is never silently changed.
 */
object PriceFormat {

    sealed interface ParseResult {
        /** [money] is null when the input was empty: no price. */
        data class Valid(val money: Money?) : ParseResult
        data object Negative : ParseResult
        data object TooPrecise : ParseResult
        data object Invalid : ParseResult
    }

    fun parse(text: String, currencyCode: String, locale: Locale): ParseResult {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val normalized = text
            .filterNot { it.isWhitespace() || it == ' ' || it == ' ' }
            .let { if (symbols.groupingSeparator != symbols.decimalSeparator) it.replace(symbols.groupingSeparator.toString(), "") else it }
            .replace(symbols.decimalSeparator, '.')
        if (normalized.isEmpty()) return ParseResult.Valid(null)
        if (normalized.startsWith('-') || normalized.startsWith(symbols.minusSign)) return ParseResult.Negative
        if (!DECIMAL.matches(normalized)) return ParseResult.Invalid

        val amount = BigDecimal(normalized)
        val fractionDigits = CurrencyMinorUnits.fractionDigits(currencyCode)
        if (amount.stripTrailingZeros().scale() > fractionDigits) return ParseResult.TooPrecise
        val amountMinor = try {
            amount.movePointRight(fractionDigits).longValueExact()
        } catch (_: ArithmeticException) {
            return ParseResult.Invalid
        }
        return ParseResult.Valid(Money(amountMinor, currencyCode))
    }

    /** For display, e.g. "12 500 Ft" in Hungarian or "€12.50" in English. */
    fun format(money: Money, locale: Locale): String {
        val fractionDigits = CurrencyMinorUnits.fractionDigits(money.currencyCode)
        val formatter = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(money.currencyCode)
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }
        return formatter.format(money.toDecimal())
    }

    /** For pre-filling the price field: plain digits with the locale's decimal separator, no grouping. */
    fun formatForInput(money: Money, locale: Locale): String =
        money.toDecimal().toPlainString().replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

    private fun Money.toDecimal(): BigDecimal =
        BigDecimal.valueOf(amountMinor, CurrencyMinorUnits.fractionDigits(currencyCode))

    private val DECIMAL = Regex("""\d+(\.\d+)?""")
}
