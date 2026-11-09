package app.giftiary.presentation.giftideas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.presentation.gifts.giftPrice
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

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
            .background(GiftiaryTheme.colors.surfaceContext, GiftiaryShapes.input)
            .padding(horizontal = GiftiarySpacing.s, vertical = GiftiarySpacing.xs / 2),
        style = GiftiaryType.chip,
        color = GiftiaryTheme.colors.textPrimary,
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
    val colors = GiftiaryTheme.colors
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

/**
 * One idea in a list: what it is, for whom, how far along, and what it may cost. [selectable] rows,
 * beside a detail pane, show and announce whether they are the one open there. [horizontalPadding]
 * lines the text up with the page's gutter while the whole width stays clickable.
 */
@Composable
fun GiftIdeaRow(
    entry: GiftIdeaEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRecipient: Boolean = true,
    selectable: Boolean = false,
    selected: Boolean = false,
    horizontalPadding: Dp = GiftiarySpacing.l,
) {
    val colors = GiftiaryTheme.colors
    val idea = entry.idea
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) colors.surfaceContext else Color.Transparent)
            .then(if (selectable) Modifier.semantics { this.selected = selected } else Modifier)
            .clickable(onClickLabel = stringResource(R.string.gift_idea_open), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = horizontalPadding, vertical = GiftiarySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
    ) {
        Text(text = idea.name, style = GiftiaryType.contentTitle, color = colors.textPrimary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
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
                    style = GiftiaryType.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
