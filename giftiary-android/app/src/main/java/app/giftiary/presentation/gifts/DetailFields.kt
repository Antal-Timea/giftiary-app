package app.giftiary.presentation.gifts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.RowChevron
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/**
 * Who or what a detail belongs to, on aqua under the title: the person an idea is for, the list a
 * wish is on. With [onClick] it leads there, and says so with a chevron.
 */
@Composable
fun DetailContextCard(
    name: String,
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.infoSurface, GiftiaryShapes.container)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 64.dp)
            .padding(start = 14.dp, end = GiftiarySpacing.m, top = GiftiarySpacing.m, bottom = GiftiarySpacing.m),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GiftiaryAvatar(name = name, size = 42.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = GiftiaryType.rowTitle, color = colors.textPrimary)
            supporting?.let { Text(text = it, style = GiftiaryType.bodySmall, color = colors.textSecondary) }
        }
        trailing()
        if (onClick != null) RowChevron()
    }
}

/** One fact of a detail: its name on the left, the value on the right, as on the gift idea boards. */
@Composable
fun DetailFactRow(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 56.dp)
            .padding(vertical = GiftiarySpacing.s),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = GiftiaryType.fieldValue, color = GiftiaryTheme.colors.textLabel)
        value()
    }
}

/** A price or an amount, with a quieter second line such as the estimate beside what was paid. */
@Composable
fun DetailFactValue(value: String, secondary: String? = null) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text = value, style = GiftiaryType.actionLarge, color = GiftiaryTheme.colors.textPrimary, textAlign = TextAlign.End)
        secondary?.let { Text(text = it, style = GiftiaryType.metadata, color = GiftiaryTheme.colors.textMuted, textAlign = TextAlign.End) }
    }
}

/** The stored product link, opened in the browser on tap; the address itself stays out of the way. */
@Composable
fun DetailLinkValue(url: String) {
    val uriHandler = LocalUriHandler.current
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier
            // A stored link may not be openable (no browser, unusual scheme); that is not an app error.
            .clickable(onClickLabel = stringResource(R.string.gift_product_url)) { runCatching { uriHandler.openUri(url) } }
            .heightIn(min = 48.dp)
            .padding(horizontal = GiftiarySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_open_link), contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textPrimary)
        Text(text = stringResource(R.string.wish_view_link), style = GiftiaryType.actionLarge, color = colors.textPrimary)
    }
}

/** What the user wrote down, as plain reading text below the facts. */
@Composable
fun DetailNote(note: String, modifier: Modifier = Modifier) {
    Text(
        text = note,
        modifier = modifier.padding(top = GiftiarySpacing.xl),
        style = GiftiaryType.body.copy(fontSize = GiftiaryType.actionLarge.fontSize),
        color = GiftiaryTheme.colors.textPrimary,
    )
}
