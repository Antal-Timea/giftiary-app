package app.gifty.presentation.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import app.gifty.R
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme

/** A labelled value on a detail screen. */
@Composable
fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = GiftyTheme.colors.textSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = GiftyTheme.colors.textPrimary)
    }
}

/** The stored product link, opened in the browser on tap. */
@Composable
fun ProductLink(url: String) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs)) {
        Text(
            text = stringResource(R.string.gift_product_url),
            style = MaterialTheme.typography.labelMedium,
            color = GiftyTheme.colors.textSecondary,
        )
        TextButton(
            // A stored link may not be openable (no browser, unusual scheme); that is not an app error.
            onClick = { runCatching { uriHandler.openUri(url) } },
            colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.primaryActionText),
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}
