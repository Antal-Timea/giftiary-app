package app.giftiary.presentation.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.giftiary.R
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/** Signed in, but the account's data on this device could not be prepared; nothing is open. */
@Composable
fun AccountPreparationFailedScreen(onRetry: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Scaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xxxl),
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.account_preparation_failed_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(GiftiarySpacing.l))
            Text(
                text = stringResource(R.string.account_preparation_failed_message),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            GiftiaryPrimaryButton(
                text = stringResource(R.string.account_preparation_retry),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GiftiarySpacing.m))
            GiftiarySecondaryButton(
                text = stringResource(R.string.auth_sign_out),
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun AccountPreparationFailedPreview() {
    GiftiaryTheme {
        AccountPreparationFailedScreen(onRetry = {}, onSignOut = {})
    }
}
