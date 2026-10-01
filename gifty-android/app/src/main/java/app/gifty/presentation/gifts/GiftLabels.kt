package app.gifty.presentation.gifts

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gifty.R
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.Money
import app.gifty.domain.repository.GiftEntry
import app.gifty.presentation.occasions.OccasionDateFormatter
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme
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

/**
 * Names the people involved: the other person for the user's own gifts, or "Giver → Recipient"
 * for a gift between two other people. Null if the person is unknown.
 */
@Composable
@ReadOnlyComposable
fun giftPeople(entry: GiftEntry): String? {
    val giver = entry.giver?.name
    val recipient = entry.recipient?.name
    return when {
        giver != null && recipient != null -> stringResource(R.string.gift_between_people, giver, recipient)
        entry.gift.direction == GiftDirection.GIVEN -> recipient
        else -> giver
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

@Composable
@ReadOnlyComposable
fun directionColor(direction: GiftDirection): Color = when (direction) {
    GiftDirection.GIVEN -> GiftyTheme.colors.directionGiven
    GiftDirection.RECEIVED -> GiftyTheme.colors.directionReceived
}

/** Direction as icon plus text in its semantic color, so it never relies on color alone. */
@Composable
fun GiftDirectionTag(direction: GiftDirection, modifier: Modifier = Modifier) {
    val color = directionColor(direction)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(
                when (direction) {
                    GiftDirection.GIVEN -> R.drawable.ic_gift_given
                    GiftDirection.RECEIVED -> R.drawable.ic_gift_received
                },
            ),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Spacer(Modifier.width(GiftySpacing.xs))
        Text(
            text = giftDirectionLabel(direction),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}
