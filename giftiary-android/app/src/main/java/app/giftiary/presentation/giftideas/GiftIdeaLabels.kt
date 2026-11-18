package app.giftiary.presentation.giftideas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.presentation.components.GiftThumbnail
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.gifts.giftPrice
import app.giftiary.presentation.gifts.rememberDecodedImage
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

/**
 * Idea, Planned or Purchased; any of them can be chosen, so mistakes are easy to correct. Three equal
 * segments side by side, as no status matters more than another, while the longest label fits its third
 * on one line; the side padding is kept compact so the Hungarian "Megvásárolva" fits on a phone. Only
 * when it cannot, on a narrow window or at large text, are they stacked one under another, so no label
 * is squeezed, broken or cut.
 */
@Composable
fun GiftIdeaStatusSelector(
    selected: GiftIdeaStatus,
    onSelect: (GiftIdeaStatus) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GiftiaryTheme.colors
    val labels = GiftIdeaStatus.entries.associateWith { giftIdeaStatusLabel(it) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val longestLabel = with(density) {
            labels.values.maxOf { measurer.measure(it, GiftiaryType.actionSmall, maxLines = 1).size.width }.toDp()
        }
        val third = (maxWidth - 1.dp * (labels.size - 1)) / labels.size
        val sideBySide = longestLabel + STATUS_OPTION_PADDING * 2 <= third
        val options: @Composable (optionModifier: (GiftIdeaStatus) -> Modifier) -> Unit = { optionModifier ->
            GiftIdeaStatus.entries.forEachIndexed { index, status ->
                if (index > 0) {
                    val line = if (sideBySide) Modifier.fillMaxHeight().width(1.dp) else Modifier.fillMaxWidth().height(1.dp)
                    Box(line.background(colors.border))
                }
                StatusOption(
                    label = labels.getValue(status),
                    selected = status == selected,
                    enabled = enabled,
                    onClick = { onSelect(status) },
                    modifier = optionModifier(status),
                )
            }
        }
        val group = Modifier
            .fillMaxWidth()
            .clip(GiftiaryShapes.input)
            .border(1.dp, colors.border, GiftiaryShapes.input)
            .selectableGroup()
        if (sideBySide) {
            Row(group.height(IntrinsicSize.Min)) {
                options { Modifier.weight(1f).fillMaxHeight() }
            }
        } else {
            Column(group) { options { Modifier.fillMaxWidth() } }
        }
    }
}

@Composable
private fun StatusOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = GiftiaryTheme.colors
    Box(
        modifier = modifier
            .background(if (selected) colors.surfaceContext else colors.background)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = STATUS_OPTION_PADDING, vertical = GiftiarySpacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            style = GiftiaryType.actionSmall,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private val STATUS_OPTION_PADDING = GiftiarySpacing.xs

/** The same size as a wish's thumbnail, so a photo reads alike in every list. */
internal val IDEA_THUMBNAIL = 72.dp
internal const val IDEA_THUMBNAIL_TAG = "idea_thumbnail"
internal const val IDEA_PHOTO_TAG = "idea_photo"
private const val DISABLED_ALPHA = 0.38f

/**
 * One idea in a list: what it is, for whom, how far along, and what it may cost. [selectable] rows,
 * beside a detail pane, show and announce whether they are the one open there. [horizontalPadding]
 * lines the text up with the page's gutter while the whole width stays clickable.
 *
 * An idea with a photo leads with a thumbnail sized and cropped like a wish's: its [photo], or the
 * branded glyph while it is read or not available now. An idea without one stays a text row.
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
    photo: LoadedPhoto? = null,
) {
    val colors = GiftiaryTheme.colors
    val idea = entry.idea
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) colors.surfaceContext else Color.Transparent)
            .then(if (selectable) Modifier.semantics { this.selected = selected } else Modifier)
            .clickable(onClickLabel = stringResource(R.string.gift_idea_open), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = horizontalPadding, vertical = GiftiarySpacing.m),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (idea.photoPath != null) IdeaThumbnail(photo)
        IdeaRowText(entry, showRecipient, Modifier.weight(1f))
    }
}

@Composable
private fun IdeaThumbnail(photo: LoadedPhoto?) {
    val image = rememberDecodedImage(photo, IDEA_THUMBNAIL)
    GiftThumbnail(
        modifier = Modifier.testTag(IDEA_THUMBNAIL_TAG),
        size = IDEA_THUMBNAIL,
        shape = GiftiaryShapes.photoCard,
        photo = image?.let {
            // The idea's name is written beside it; the photo adds no information of its own.
            {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().testTag(IDEA_PHOTO_TAG),
                    contentScale = ContentScale.Crop,
                )
            }
        },
    )
}

@Composable
private fun IdeaRowText(entry: GiftIdeaEntry, showRecipient: Boolean, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    val idea = entry.idea
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs)) {
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
