package app.gifty.domain.model

/**
 * An amount in the smallest unit of [currencyCode] (e.g. cents, fillér).
 */
data class Money(
    val amountMinor: Long,
    val currencyCode: String,
) {
    init {
        require(amountMinor >= 0) { "amountMinor must not be negative: $amountMinor" }
        require(CURRENCY_CODE.matches(currencyCode)) {
            "currencyCode must be three uppercase letters: '$currencyCode'"
        }
    }

    private companion object {
        val CURRENCY_CODE = Regex("[A-Z]{3}")
    }
}
