package app.giftiary.presentation.wishlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.model.Money
import app.giftiary.presentation.components.GiftThumbnail
import app.giftiary.presentation.components.GiftiaryBackButton
import app.giftiary.presentation.components.monogramInitial
import app.giftiary.presentation.gifts.giftPrice
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/** Who a wishlist is for, as shown to the user. */
sealed interface WishlistOwner {
    data object Me : WishlistOwner
    data class Person(val name: String) : WishlistOwner

    /** The person was deleted; the wishlist stays, but there is no one to open. */
    data object PersonRemoved : WishlistOwner
}

@Composable
internal fun wishlistOwnerText(owner: WishlistOwner): String = when (owner) {
    WishlistOwner.Me -> stringResource(R.string.wishlist_for_me)
    is WishlistOwner.Person -> stringResource(R.string.wishlist_for_person, owner.name)
    WishlistOwner.PersonRemoved -> stringResource(R.string.wishlist_person_removed)
}

/**
 * A list's cover. Lists have no cover image of their own, so every list gets the branded fallback
 * the approved board draws for one without: its initial on an aqua rounded square. Decorative,
 * as the name is always written beside it.
 */
@Composable
internal fun ListCover(name: String, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(GiftiaryShapes.photoCard)
            .background(GiftiaryTheme.colors.avatarFallback),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogramInitial(name),
            modifier = Modifier.clearAndSetSemantics {},
            style = GiftiaryType.sheetTitle.copy(fontFamily = GiftiaryType.rowTitle.fontFamily),
            color = GiftiaryTheme.colors.textPrimary,
        )
    }
}

/**
 * The top of a wishlist, owner's or guest's: back, then the list's identity scaled up from its
 * Lists row. [options] sits opposite back; [below] follows the meta line under the title.
 */
@Composable
internal fun WishlistHeader(
    title: String,
    meta: String,
    onBack: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onMetaClick: (() -> Unit)? = null,
    metaClickLabel: String? = null,
    options: @Composable () -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.fieldGroup)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GiftiaryBackButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
            options()
        }
        Row(
            modifier = Modifier.padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, modifier = Modifier.semantics { heading() }, style = GiftiaryType.screenTitle, color = colors.textPrimary)
                Text(
                    text = meta,
                    modifier = Modifier
                        .then(
                            // Only a person who can be opened makes the line a link.
                            if (onMetaClick != null) {
                                Modifier
                                    .clickable(onClickLabel = metaClickLabel, onClick = onMetaClick)
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = GiftiarySpacing.s)
                            } else {
                                Modifier.padding(top = 6.dp)
                            },
                        ),
                    style = GiftiaryType.label.copy(fontWeight = GiftiaryType.metadata.fontWeight),
                    color = colors.textLabel,
                )
                below()
            }
        }
    }
}

/**
 * One wish as both the owner and guests see it: the thumbnail, its name, note, estimated price and
 * link. [trailing] is the owner's options; [below] the guest's reservation.
 */
@Composable
internal fun WishRowContent(
    name: String,
    note: String?,
    price: Money?,
    productUrl: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.l),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Wishes have no photos yet; the branded glyph tile keeps the row's shape.
        GiftThumbnail(size = WISH_THUMBNAIL, shape = GiftiaryShapes.photoCard)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = GiftiaryType.actionLarge, color = colors.textPrimary)
            note?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 3.dp),
                    style = GiftiaryType.bodySmall,
                    color = colors.textLabel,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WishMeta(price = price, productUrl = productUrl?.takeIf { it.isWebLink() })
            below()
        }
        trailing()
    }
}

@Composable
private fun WishMeta(price: Money?, productUrl: String?) {
    if (price == null && productUrl == null) return
    val colors = GiftiaryTheme.colors
    val uriHandler = LocalUriHandler.current
    Row(modifier = Modifier.padding(top = GiftiarySpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        price?.let { Text(stringResource(R.string.wish_price_estimate, giftPrice(it)), style = GiftiaryType.metadata, color = colors.textMuted) }
        if (price != null && productUrl != null) {
            Text(" · ", modifier = Modifier.clearAndSetSemantics {}, style = GiftiaryType.metadata, color = colors.textMuted)
        }
        productUrl?.let { url ->
            Row(
                modifier = Modifier
                    .trimVertical(14.dp)
                    // A stored link may not be openable (no browser, unusual scheme); that is not an app error.
                    .clickable { runCatching { uriHandler.openUri(url) } }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = GiftiarySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_open_link), contentDescription = null, modifier = Modifier.size(11.dp), tint = colors.textLabel)
                Spacer(Modifier.width(GiftiarySpacing.xs))
                Text(stringResource(R.string.wish_view_link), style = GiftiaryType.metadata, color = colors.textLabel)
            }
        }
    }
}

// Only web links are offered for opening, the user's own as much as someone else's.
private fun String.isWebLink(): Boolean = startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

internal val WISH_THUMBNAIL = 72.dp

/**
 * Lays a 48dp target out as if it were [inset] shorter at top and bottom, as the boards' small
 * inline actions do with negative margins: the whole target still takes touches.
 */
internal fun Modifier.trimVertical(inset: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val trim = inset.roundToPx()
    layout(placeable.width, (placeable.height - 2 * trim).coerceAtLeast(0)) { placeable.place(0, -trim) }
}
