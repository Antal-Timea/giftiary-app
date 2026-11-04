package app.giftiary.presentation.gifts

import app.giftiary.domain.model.CurrencyMinorUnits
import app.giftiary.domain.model.Money
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Converts between what people type or read and [Money], without floating point.
 *
 * Input is deliberately simple and locale-independent: digits with at most one decimal
 * separator, which may be "." or "," since keypads offer one or the other. Grouping separators
 * are not accepted, because "12,500" cannot be read safely as either 12.5 or 12 500. Input with
 * more decimals than the currency has is rejected rather than rounded, so a price is never
 * silently changed. Display formatting, by contrast, follows the locale.
 */
object PriceFormat {

    sealed interface ParseResult {
        /** [money] is null when the input was blank: no price. */
        data class Valid(val money: Money?) : ParseResult
        data object Negative : ParseResult
        data object TooPrecise : ParseResult
        data object Invalid : ParseResult
    }

    fun parse(text: String, currencyCode: String): ParseResult {
        val input = text.trim()
        if (input.isEmpty()) return ParseResult.Valid(null)
        if (!AMOUNT.matches(input)) {
            val isNegativeNumber = input.first() in MINUS_SIGNS && AMOUNT.matches(input.drop(1))
            return if (isNegativeNumber) ParseResult.Negative else ParseResult.Invalid
        }

        val amount = BigDecimal(input.replace(',', '.'))
        val fractionDigits = CurrencyMinorUnits.fractionDigits(currencyCode)
        // Trailing zeros carry no precision, so "12500,00" is still a whole number of forints.
        if (amount.stripTrailingZeros().scale() > fractionDigits) return ParseResult.TooPrecise
        val amountMinor = try {
            amount.movePointRight(fractionDigits).longValueExact()
        } catch (_: ArithmeticException) {
            return ParseResult.Invalid
        }
        return ParseResult.Valid(Money(amountMinor, currencyCode))
    }

    /**
     * For display: the amount first, then the currency, e.g. "12 500 Ft" in Hungarian and
     * "12,990 HUF" or "12.50 EUR" in English. Hungarian keeps its own currency formatting; elsewhere
     * the locale's currency symbol would lead ("HUF12,990"), which is not how Giftiary shows prices.
     */
    fun format(money: Money, locale: Locale): String {
        val fractionDigits = CurrencyMinorUnits.fractionDigits(money.currencyCode)
        if (locale.language == HUNGARIAN) {
            val formatter = NumberFormat.getCurrencyInstance(locale).apply {
                currency = Currency.getInstance(money.currencyCode)
                minimumFractionDigits = fractionDigits
                maximumFractionDigits = fractionDigits
            }
            return formatter.format(money.toDecimal())
        }
        val amount = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }.format(money.toDecimal())
        // A no-break space, so the code never wraps away from its amount.
        return "$amount\u00A0${money.currencyCode}"
    }

    /** For pre-filling the price field: plain digits with the locale's decimal separator, no grouping. */
    fun formatForInput(money: Money, locale: Locale): String =
        money.toDecimal().toPlainString().replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

    private fun Money.toDecimal(): BigDecimal =
        BigDecimal.valueOf(amountMinor, CurrencyMinorUnits.fractionDigits(currencyCode))

    private const val HUNGARIAN = "hu"
    private val AMOUNT = Regex("""[0-9]+([.,][0-9]+)?""")
    private const val MINUS_SIGNS = "-\u2212"
}

enum class PriceError {
    INVALID,
    NEGATIVE,
    TOO_PRECISE,
}

/** Why a typed price cannot be stored, or null when it can; shared by every editor with a price. */
internal fun PriceFormat.ParseResult.toError(): PriceError? = when (this) {
    is PriceFormat.ParseResult.Valid -> null
    PriceFormat.ParseResult.Negative -> PriceError.NEGATIVE
    PriceFormat.ParseResult.TooPrecise -> PriceError.TOO_PRECISE
    PriceFormat.ParseResult.Invalid -> PriceError.INVALID
}
