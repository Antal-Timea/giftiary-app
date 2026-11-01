package app.giftiary.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.model.GiftDirection
import app.giftiary.presentation.theme.GiftiaryColors
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/**
 * A filter chip. The selected chip is a solid teal fill with ivory text. A [direction] chip is
 * outlined in its direction's colour and always carries its arrow, so direction never depends on
 * colour alone.
 */
@Composable
fun GiftiaryFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    direction: GiftDirection? = null,
) {
    val colors = GiftiaryTheme.colors
    val content = when {
        selected -> colors.background
        direction != null -> directionColor(direction)
        else -> colors.textPrimary
    }
    val shape = GiftiaryShapes.pill
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .selectable(selected = selected, onClick = onClick)
            .then(
                if (selected) {
                    Modifier.background(colors.selected, shape)
                } else {
                    Modifier.border(1.4.dp, chipOutline(colors, direction), shape)
                },
            )
            .padding(if (direction == null) NeutralPadding else DirectionPadding),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (direction != null) {
            Icon(
                painter = painterResource(direction.arrow),
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = content,
            )
        }
        Text(text = text, style = GiftiaryType.chip, color = content)
    }
}

/** The colour that, with the word and the arrow, carries a gift's direction. */
@Composable
@ReadOnlyComposable
fun directionColor(direction: GiftDirection): Color = when (direction) {
    GiftDirection.GIVEN -> GiftiaryTheme.colors.directionGiven
    GiftDirection.RECEIVED -> GiftiaryTheme.colors.directionReceived
}

/** Given points away from the user, received points back. */
val GiftDirection.arrow: Int
    get() = when (this) {
        GiftDirection.GIVEN -> R.drawable.ic_arrow_right
        GiftDirection.RECEIVED -> R.drawable.ic_arrow_left
    }

private fun chipOutline(colors: GiftiaryColors, direction: GiftDirection?): Color = when (direction) {
    GiftDirection.GIVEN -> colors.directionGiven.copy(alpha = 0.35f)
    GiftDirection.RECEIVED -> colors.directionReceived.copy(alpha = 0.32f)
    null -> colors.borderStrong
}

private val NeutralPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
private val DirectionPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp)
