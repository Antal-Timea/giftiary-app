package app.giftiary.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.presentation.components.GiftiaryPillButton
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/**
 * Covers the app while a deleted account is removed from this device. [failure] is set when that
 * could not finish yet; the account stays closed until a retry succeeds. Nothing of the account is
 * shown, and there is no way back to it.
 */
@Composable
fun FinishingAccountDeletionScreen(failure: AccountDeletionFailure?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    DeletionStatePage(modifier) {
        if (failure == null) {
            CircularProgressIndicator(color = GiftiaryTheme.colors.primaryAction)
            StateTitle(stringResource(R.string.account_deletion_finishing), Modifier.padding(top = GiftiarySpacing.xl))
        } else {
            StateTitle(stringResource(R.string.account_deletion_not_finished))
            StateMessage(
                stringResource(
                    when (failure) {
                        AccountDeletionFailure.NETWORK -> R.string.sharing_network_error
                        AccountDeletionFailure.SERVER -> R.string.sharing_server_error
                    },
                ),
            )
            GiftiaryPillButton(
                text = stringResource(R.string.sharing_retry),
                onClick = onRetry,
                modifier = Modifier.padding(top = GiftiarySpacing.xl),
            )
        }
    }
}

/** The end of a deletion, before the signed-out side opens: no name, no email, nothing of the account. */
@Composable
fun AccountDeletedScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    DeletionStatePage(
        modifier = modifier,
        action = {
            GiftiaryPrimaryButton(
                text = stringResource(R.string.auth_continue),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .background(GiftiaryTheme.colors.thumbnailFallback, GiftiaryShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = GiftiaryTheme.colors.textLabel,
            )
        }
        StateTitle(stringResource(R.string.account_deleted_title), Modifier.padding(top = GiftiarySpacing.xl))
        StateMessage(stringResource(R.string.account_deleted_message))
    }
}

/**
 * A DS-05 system state on the ivory page, with no back circle: what is happening, centred in the
 * height, and the [action] that ends it at the foot. Changes are announced as they happen.
 */
@Composable
private fun DeletionStatePage(
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(modifier = modifier, containerColor = GiftiaryTheme.colors.background) { contentPadding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(contentPadding)) {
            // At least the screen's height, so the state sits in the middle and the action at the
            // foot; large text makes it longer, and it scrolls.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .giftiaryReadableWidth()
                    .padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.xl),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier)
                Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, content = content)
                if (action != null) Box(Modifier.padding(top = GiftiarySpacing.xl)) { action() } else Spacer(Modifier)
            }
        }
    }
}

@Composable
private fun StateTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = GiftiaryType.screenTitle,
        color = GiftiaryTheme.colors.textPrimary,
    )
}

@Composable
private fun StateMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = GiftiarySpacing.s),
        style = GiftiaryType.body,
        color = GiftiaryTheme.colors.textSecondary,
    )
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
