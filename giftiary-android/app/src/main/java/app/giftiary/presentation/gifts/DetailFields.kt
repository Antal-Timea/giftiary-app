package app.giftiary.presentation.gifts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
 *
 * The [trailing] status sits beside the text while the title still fits on one line and no word of
 * [supporting] has to break; otherwise it moves below the text, so large text or a long translation
 * is never squeezed into a column a few letters wide.
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
    Layout(
        content = {
            GiftiaryAvatar(name = name, size = 42.dp, modifier = Modifier.layoutId(ContextSlot.Avatar))
            Text(text = title, modifier = Modifier.layoutId(ContextSlot.Title), style = GiftiaryType.rowTitle, color = colors.textPrimary)
            supporting?.let {
                Text(text = it, modifier = Modifier.layoutId(ContextSlot.Supporting), style = GiftiaryType.bodySmall, color = colors.textSecondary)
            }
            Row(
                modifier = Modifier.layoutId(ContextSlot.Trailing),
                horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
            if (onClick != null) Box(Modifier.layoutId(ContextSlot.Chevron)) { RowChevron() }
        },
        modifier = modifier
            .fillMaxWidth()
            .background(colors.infoSurface, GiftiaryShapes.container)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 64.dp)
            .padding(start = 14.dp, end = GiftiarySpacing.m, top = GiftiarySpacing.m, bottom = GiftiarySpacing.m),
    ) { measurables, constraints ->
        fun slot(id: ContextSlot) = measurables.firstOrNull { it.layoutId == id }
        val width = constraints.maxWidth
        val gap = GiftiarySpacing.m.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val avatar = slot(ContextSlot.Avatar)!!.measure(loose)
        val chevron = slot(ContextSlot.Chevron)?.measure(loose)
        val chevronSpace = chevron?.let { it.width + gap } ?: 0
        val titleMeasurable = slot(ContextSlot.Title)!!
        val supportingMeasurable = slot(ContextSlot.Supporting)
        val trailingMeasurable = slot(ContextSlot.Trailing)!!
        val trailingWidth = trailingMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        val besideText = width - avatar.width - gap - chevronSpace - (if (trailingWidth > 0) trailingWidth + gap else 0)
        val sideBySide = trailingWidth == 0 || (
            titleMeasurable.maxIntrinsicWidth(Constraints.Infinity) <= besideText &&
                (supportingMeasurable?.minIntrinsicWidth(Constraints.Infinity) ?: 0) <= besideText
            )
        val textWidth = if (sideBySide) besideText else width - avatar.width - gap - chevronSpace
        val textConstraints = loose.copy(maxWidth = textWidth.coerceAtLeast(0))
        val title = titleMeasurable.measure(textConstraints)
        val supportingLine = supportingMeasurable?.measure(textConstraints)
        val trailingItem = trailingMeasurable.measure(if (sideBySide) loose else textConstraints)
        val textHeight = title.height + (supportingLine?.height ?: 0)
        val stackGap = if (sideBySide || trailingItem.height == 0) 0 else GiftiarySpacing.s.roundToPx()
        val block = if (sideBySide) textHeight else textHeight + stackGap + trailingItem.height
        val height = maxOf(block, avatar.height, trailingItem.height, chevron?.height ?: 0, constraints.minHeight)
        val textX = avatar.width + gap
        layout(width, height) {
            avatar.place(0, (height - avatar.height) / 2)
            val top = (height - block) / 2
            title.place(textX, top)
            supportingLine?.place(textX, top + title.height)
            if (sideBySide) {
                trailingItem.place(textX + textWidth + gap, (height - trailingItem.height) / 2)
            } else {
                trailingItem.place(textX, top + textHeight + stackGap)
            }
            chevron?.let { it.place(width - it.width, (height - it.height) / 2) }
        }
    }
}

private enum class ContextSlot { Avatar, Title, Supporting, Trailing, Chevron }

/**
 * One fact of a detail: its name on the left, the value on the right, as on the gift idea boards.
 * When the name would not fit on one line beside the value (large text, long translations) the
 * value moves below it, still on the right, so the name is never squeezed into a column that splits
 * its words.
 */
@Composable
fun DetailFactRow(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Layout(
        content = {
            Text(text = label, style = GiftiaryType.fieldValue, color = GiftiaryTheme.colors.textLabel)
            Box { value() }
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 56.dp)
            .padding(vertical = GiftiarySpacing.s),
    ) { measurables, constraints ->
        val (labelMeasurable, valueMeasurable) = measurables
        val width = constraints.maxWidth
        val gap = GiftiarySpacing.l.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val value = valueMeasurable.measure(loose)
        val sideBySide = labelMeasurable.maxIntrinsicWidth(Constraints.Infinity) + gap + value.width <= width
        if (sideBySide) {
            val label = labelMeasurable.measure(loose.copy(maxWidth = width - gap - value.width))
            val height = maxOf(label.height, value.height, constraints.minHeight)
            layout(width, height) {
                label.place(0, (height - label.height) / 2)
                value.place(width - value.width, (height - value.height) / 2)
            }
        } else {
            val label = labelMeasurable.measure(loose)
            val stackGap = GiftiarySpacing.xs.roundToPx()
            val content = label.height + stackGap + value.height
            val height = maxOf(content, constraints.minHeight)
            val top = (height - content) / 2
            layout(width, height) {
                label.place(0, top)
                value.place(width - value.width, top + label.height + stackGap)
            }
        }
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
        Text(text = stringResource(R.string.wish_view_link), style = GiftiaryType.actionSmall, color = colors.textPrimary)
    }
}

/** What the user wrote down, as plain reading text below the facts. */
@Composable
fun DetailNote(note: String, modifier: Modifier = Modifier) {
    Text(
        text = note,
        modifier = modifier.padding(top = GiftiarySpacing.xl),
        style = GiftiaryType.body,
        color = GiftiaryTheme.colors.textPrimary,
    )
}
