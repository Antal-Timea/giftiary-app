package app.giftiary.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.giftiary.R
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/**
 * Covers the app while a deleted account is removed from this device. [failure] is set when that
 * could not finish yet; the account stays closed until a retry succeeds.
 */
@Composable
fun FinishingAccountDeletionScreen(failure: AccountDeletionFailure?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Scaffold(modifier = modifier, containerColor = colors.background) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .giftiaryReadableWidth()
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xxxl)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l, Alignment.CenterVertically),
        ) {
            if (failure == null) {
                CircularProgressIndicator(color = colors.primaryAction)
                Text(
                    text = stringResource(R.string.account_deletion_finishing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
            } else {
                Text(
                    text = stringResource(R.string.account_deletion_not_finished),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(
                        when (failure) {
                            AccountDeletionFailure.NETWORK -> R.string.sharing_network_error
                            AccountDeletionFailure.SERVER -> R.string.sharing_server_error
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                GiftiaryPrimaryButton(
                    text = stringResource(R.string.sharing_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun AccountDeletedScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .giftiaryReadableWidth()
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xxxl),
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.account_deleted_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = GiftiaryTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(GiftiarySpacing.l))
            Text(
                text = stringResource(R.string.account_deleted_message),
                style = MaterialTheme.typography.bodyLarge,
                color = GiftiaryTheme.colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            GiftiaryPrimaryButton(
                text = stringResource(R.string.auth_continue),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FinishingFailedPreview() {
    GiftiaryTheme {
        FinishingAccountDeletionScreen(failure = AccountDeletionFailure.NETWORK, onRetry = {})
    }
}

@PreviewLightDark
@Composable
private fun AccountDeletedPreview() {
    GiftiaryTheme {
        AccountDeletedScreen(onContinue = {})
    }
}
