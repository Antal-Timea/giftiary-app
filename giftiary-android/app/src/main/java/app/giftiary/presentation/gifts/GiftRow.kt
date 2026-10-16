package app.giftiary.presentation.gifts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/**
 * One gift in a list: what it was, given or received, with whom, and when. [showPeople] is off
 * where the person is already obvious, e.g. on that person's own page.
 */
@Composable
fun GiftRow(entry: GiftEntry, onClick: () -> Unit, modifier: Modifier = Modifier, showPeople: Boolean = true) {
    val colors = GiftiaryTheme.colors
    val gift = entry.gift
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.gift_open), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
    ) {
        Text(text = gift.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            GiftDirectionTag(gift.direction)
            val people = if (showPeople) giftPeople(entry) else null
            val details = listOfNotNull(people, giftDate(gift.givenAt), entry.occasion?.let { occasionTitle(it) })
            Text(
                text = details.joinToString(separator = SEPARATOR),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

private const val SEPARATOR = " · "
