package app.giftiary.presentation.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
