package app.gifty.presentation.giftideas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gifty.R
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.repository.GiftIdeaEntry
import app.gifty.presentation.gifts.giftPrice
import app.gifty.presentation.occasions.occasionTitle
import app.gifty.presentation.theme.GiftyShapes
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme

@Composable
@ReadOnlyComposable
fun giftIdeaStatusLabel(status: GiftIdeaStatus): String = stringResource(
    when (status) {
        GiftIdeaStatus.IDEA -> R.string.gift_idea_status_idea
        GiftIdeaStatus.PLANNED -> R.string.gift_idea_status_planned
        GiftIdeaStatus.PURCHASED -> R.string.gift_idea_status_purchased
    },
)

/** Status as a quiet text tag; the text carries the meaning, not the background. */
@Composable
fun GiftIdeaStatusTag(status: GiftIdeaStatus, modifier: Modifier = Modifier) {
    Text(
        text = giftIdeaStatusLabel(status),
        modifier = modifier
            .background(GiftyTheme.colors.surfaceContext, GiftyShapes.input)
            .padding(horizontal = GiftySpacing.s, vertical = GiftySpacing.xs / 2),
        style = MaterialTheme.typography.labelMedium,
        color = GiftyTheme.colors.textPrimary,
    )
}

/** Idea, Planned or Purchased; any of them can be chosen, so mistakes are easy to correct. */
@Composable
fun GiftIdeaStatusSelector(
    selected: GiftIdeaStatus,
    onSelect: (GiftIdeaStatus) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GiftyTheme.colors
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        GiftIdeaStatus.entries.forEachIndexed { index, status ->
            SegmentedButton(
                selected = status == selected,
                onClick = { onSelect(status) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = GiftIdeaStatus.entries.size),
                enabled = enabled,
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.surfaceContext,
                    activeContentColor = colors.textPrimary,
                    activeBorderColor = colors.border,
                    inactiveContainerColor = colors.background,
                    inactiveContentColor = colors.textSecondary,
                    inactiveBorderColor = colors.border,
                ),
            ) {
                Text(giftIdeaStatusLabel(status))
            }
        }
    }
}

/** One idea in a list: what it is, for whom, how far along, and what it may cost. */
@Composable
fun GiftIdeaRow(
    entry: GiftIdeaEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRecipient: Boolean = true,
) {
    val colors = GiftyTheme.colors
    val idea = entry.idea
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.gift_idea_open), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = GiftySpacing.l, vertical = GiftySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs),
    ) {
        Text(text = idea.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GiftySpacing.s),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            GiftIdeaStatusTag(idea.status)
            val price = idea.actualPrice ?: idea.estimatedPrice
            val details = listOfNotNull(
                entry.recipient?.name?.takeIf { showRecipient },
                entry.occasion?.let { occasionTitle(it) },
                price?.let { giftPrice(it) },
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(separator = " · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
