package app.giftiary.presentation.sharing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import java.util.UUID

@Composable
fun JoinWishlistScreen(viewModel: JoinWishlistViewModel, onBack: () -> Unit, onJoined: (UUID) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.phase) {
        (state.phase as? JoinPhase.Joined)?.let { onJoined(it.wishlistId) }
    }
    JoinWishlistScreen(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onJoin = viewModel::onJoin,
        onRetry = viewModel::onRetry,
    )
}

@Composable
fun JoinWishlistScreen(
    state: JoinWishlistUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onJoin: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    val joining = state.phase == JoinPhase.Joining || state.phase is JoinPhase.Joined
    val failure = (state.phase as? JoinPhase.Failed)?.failure
    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = stringResource(R.string.join_wishlist_title), onBack = onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xl),
        ) {
            Text(
                text = stringResource(R.string.join_wishlist_message),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(GiftiarySpacing.l))
            GiftiaryTextField(
                value = state.input,
                onValueChange = onInputChange,
                label = stringResource(R.string.join_wishlist_input),
                modifier = Modifier.fillMaxWidth(),
                errorMessage = failure?.let { joinFailureText(it) },
                enabled = !joining,
                // No suggestions or corrections: the keyboard should not learn an invite token.
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onJoin() }),
            )
            Spacer(Modifier.height(GiftiarySpacing.xl))
            GiftiaryPrimaryButton(
                text = stringResource(R.string.join_wishlist_join),
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.input.isNotBlank(),
                loading = joining,
            )
            if (failure?.isRetryable == true) {
                Spacer(Modifier.height(GiftiarySpacing.m))
                GiftiarySecondaryButton(
                    text = stringResource(R.string.sharing_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun joinFailureText(failure: JoinFailure): String = when (failure) {
    JoinFailure.INVALID_LINK -> stringResource(R.string.join_wishlist_invalid_link)
    JoinFailure.UNAVAILABLE -> stringResource(R.string.join_wishlist_unavailable)
    JoinFailure.NETWORK -> stringResource(R.string.sharing_network_error)
    JoinFailure.SERVER -> stringResource(R.string.sharing_server_error)
}

@PreviewLightDark
@Composable
private fun JoinWishlistPreview() {
    GiftiaryTheme {
        JoinWishlistScreen(
            state = JoinWishlistUiState(phase = JoinPhase.Failed(JoinFailure.NETWORK)),
            onBack = {},
            onInputChange = {},
            onJoin = {},
            onRetry = {},
        )
    }
}
