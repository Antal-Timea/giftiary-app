package app.giftiary.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/** The heading of a section within a list. */
@Composable
fun SectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = GiftiarySpacing.l, end = GiftiarySpacing.l, top = GiftiarySpacing.xl, bottom = GiftiarySpacing.s)
            .semantics { heading() },
        style = GiftiaryType.sectionTitle,
        color = GiftiaryTheme.colors.textPrimary,
    )
}

/**
 * A row of a list: a title, and an optional second line; the whole row opens the item.
 * [onClickLabel] is what accessibility services announce the click as doing.
 */
@Composable
fun ListRow(title: String, detail: String?, onClickLabel: String, onClick: () -> Unit) {
    GiftiaryRow(
        title = title,
        supporting = detail?.let { { RowSupportingText(it) } },
        contentPadding = PaddingValues(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.m),
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
}

/**
 * The content row of the design system: no container, an optional [leading] avatar or thumbnail,
 * the title, a [supporting] line, and an optional [trailing] element such as [RowChevron].
 * Rows are separated by a [GiftiaryDivider] indented past the leading element.
 */
@Composable
fun GiftiaryRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: @Composable (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    titleStyle: TextStyle = GiftiaryType.rowTitle,
    contentPadding: PaddingValues = PaddingValues(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.m),
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = titleStyle, color = GiftiaryTheme.colors.textPrimary)
            supporting?.invoke()
        }
        trailing?.invoke()
    }
}

/** The quiet second line of a row: relationship and next occasion, a note, a count. */
@Composable
fun RowSupportingText(text: String) {
    Text(
        text = text,
        style = GiftiaryType.bodySmall,
        color = GiftiaryTheme.colors.textLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The trailing chevron of a row that opens something; decorative, as the row is the target. */
@Composable
fun RowChevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        modifier = Modifier.size(15.dp),
        tint = GiftiaryTheme.colors.textLabel,
    )
}

/**
 * A hairline between rows. [startIndent] skips the leading element: 62dp past a 48dp avatar,
 * 86dp past a 64dp wish thumbnail, both measured from the screen gutter.
 */
@Composable
fun GiftiaryDivider(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent),
        thickness = 1.dp,
        color = GiftiaryTheme.colors.divider,
    )
}
