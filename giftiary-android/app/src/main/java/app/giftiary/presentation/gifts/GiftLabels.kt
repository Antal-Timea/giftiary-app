package app.giftiary.presentation.gifts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.giftiary.R
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Money
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.presentation.occasions.OccasionDateFormatter
import java.time.LocalDate

@Composable
@ReadOnlyComposable
fun giftDirectionLabel(direction: GiftDirection): String = stringResource(
    when (direction) {
        GiftDirection.GIVEN -> R.string.gift_direction_given
        GiftDirection.RECEIVED -> R.string.gift_direction_received
    },
)

/** The person on the other side: who it was for, or who it came from. */
@Composable
@ReadOnlyComposable
fun giftPersonLabel(direction: GiftDirection): String = stringResource(
    when (direction) {
        GiftDirection.GIVEN -> R.string.gift_recipient
        GiftDirection.RECEIVED -> R.string.gift_giver
    },
)

/** "Given to Mom", "Received from Peter", "Grandma → Martin", or the bare direction. */
@Composable
@ReadOnlyComposable
fun giftDirectionLine(entry: GiftEntry): String {
    val giver = entry.giver?.name
    val recipient = entry.recipient?.name
    if (giver != null && recipient != null) return stringResource(R.string.gift_between_people, giver, recipient)
    val other = if (entry.gift.direction == GiftDirection.GIVEN) recipient else giver
    return when (entry.gift.direction) {
        GiftDirection.GIVEN -> other?.let { stringResource(R.string.gift_given_to, it) } ?: stringResource(R.string.gift_direction_given)
        GiftDirection.RECEIVED -> other?.let { stringResource(R.string.gift_received_from, it) } ?: stringResource(R.string.gift_direction_received)
    }
}

@Composable
@ReadOnlyComposable
fun giftDate(date: LocalDate): String =
    OccasionDateFormatter.format(date.dayOfMonth, date.monthValue, date.year, LocalConfiguration.current.locales[0])

@Composable
@ReadOnlyComposable
fun giftPrice(price: Money): String = PriceFormat.format(price, LocalConfiguration.current.locales[0])

@Composable
@ReadOnlyComposable
fun priceErrorText(error: PriceError, currencyCode: String): String = when (error) {
    PriceError.INVALID -> stringResource(R.string.gift_error_price_invalid)
    PriceError.NEGATIVE -> stringResource(R.string.gift_error_price_negative)
    PriceError.TOO_PRECISE -> stringResource(R.string.gift_error_price_too_precise, currencyCode)
}
