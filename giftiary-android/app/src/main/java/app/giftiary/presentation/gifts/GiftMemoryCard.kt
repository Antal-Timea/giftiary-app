package app.giftiary.presentation.gifts

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.presentation.components.arrow
import app.giftiary.presentation.components.directionColor
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/**
 * A gift as a memory: its photo in a tall rounded card, or the branded gift glyph on aqua when it
 * has none or it cannot be read now; then its name and a direction line naming the other person,
 * and the occasion if [showOccasion].
 */
@Composable
fun GiftMemoryCard(
    entry: GiftEntry,
    photo: LoadedPhoto?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showOccasion: Boolean = false,
) {
    val colors = GiftiaryTheme.colors
    val direction = entry.gift.direction
    Column(
        modifier = modifier.clickable(onClickLabel = stringResource(R.string.gift_open), onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PHOTO_CARD_RATIO)
                .clip(GiftiaryShapes.photoCard)
                .background(colors.thumbnailFallback),
            contentAlignment = Alignment.Center,
        ) {
            val image = photo?.let { rememberDecoded(it) }
            if (image != null) {
                // The gift's name is written below; the photo adds no information of its own.
                Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_gift_glyph),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = colors.textLabel,
                )
            }
        }
        Text(
            text = entry.gift.name,
            modifier = Modifier.padding(top = 9.dp),
            style = GiftiaryType.contentTitle,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val line = listOfNotNull(
            giftDirectionLine(entry),
            entry.occasion?.takeIf { showOccasion }?.let { occasionTitle(it) },
        ).joinToString(" · ")
        Row(
            modifier = Modifier.padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(direction.arrow),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = directionColor(direction),
            )
            Text(
                text = line,
                style = GiftiaryType.label.copy(fontSize = GiftiaryType.metadata.fontSize),
                color = directionColor(direction),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val PHOTO_CARD_RATIO = 34f / 40f
