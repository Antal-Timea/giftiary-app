package app.giftiary.presentation.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/** A full-screen failure with nothing else to show, and a way to try again when that may help. */
@Composable
internal fun FailureContent(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = GiftiarySpacing.xxl, vertical = GiftiarySpacing.xl),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = GiftiaryTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) GiftiarySecondaryButton(text = stringResource(R.string.sharing_retry), onClick = onRetry)
    }
}

/** A row with a title, a detail line and one quiet action at its end. */
@Composable
internal fun ActionRow(
    title: String,
    detail: String,
    actionLabel: String,
    actionDescription: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = GiftiarySpacing.l, end = GiftiarySpacing.s, top = GiftiarySpacing.s, bottom = GiftiarySpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        TextButton(
            onClick = onAction,
            enabled = actionEnabled,
            modifier = Modifier.semantics { contentDescription = actionDescription },
            colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
        ) {
            Text(actionLabel)
        }
    }
}

/** A quiet line of text inside a list, e.g. for an empty section. */
@Composable
internal fun ListNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
        style = MaterialTheme.typography.bodyLarge,
        color = GiftiaryTheme.colors.textSecondary,
    )
}
