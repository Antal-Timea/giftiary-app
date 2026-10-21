package app.giftiary.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/** The heading of a section within a list. */
@Composable
fun SectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = GiftiarySpacing.l, end = GiftiarySpacing.l, top = GiftiarySpacing.xl, bottom = GiftiarySpacing.s)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = GiftiaryTheme.colors.textPrimary,
    )
}

/**
 * A row of a list: a title, and an optional second line; the whole row opens the item.
 * [onClickLabel] is what accessibility services announce the click as doing.
 */
@Composable
fun ListRow(title: String, detail: String?, onClickLabel: String, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
