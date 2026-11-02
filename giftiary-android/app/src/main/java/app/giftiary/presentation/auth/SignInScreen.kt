package app.giftiary.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.data.auth.ProviderSignInState
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.presentation.components.GiftiaryAdaptivePreviews
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTextAction
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    onCreateAccount: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    joinPending: Boolean = false,
    providerViewModel: ProviderSignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val providerState by providerViewModel.state.collectAsStateWithLifecycle()
    SignInContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onCreateAccount = onCreateAccount,
        onForgotPassword = { onForgotPassword(state.email) },
        providers = providerViewModel.providers,
        providerState = providerState,
        onProvider = rememberProviderClick(providerViewModel),
        joinPending = joinPending,
        modifier = modifier,
    )
}

/**
 * The approved Sign in board: the welcome, the providers this build offers, then email and
 * password, Forgot password, Sign in, and the way to a new account.
 */
@Composable
internal fun SignInContent(
    state: CredentialsUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    providers: List<AuthProvider>,
    providerState: ProviderSignInState,
    onProvider: (AuthProvider) -> Unit,
    modifier: Modifier = Modifier,
    joinPending: Boolean = false,
) {
    val colors = GiftiaryTheme.colors
    // One sign-in at a time: email and provider buttons wait for each other.
    val providerBusy = providerState.isBusy
    AuthPage(modifier, decorated = true) {
        GiftiaryTaskHeader(
            title = stringResource(R.string.auth_sign_in_title),
            subtitle = stringResource(R.string.auth_sign_in_message),
            onBack = null,
        )
        // An invite opened while signed out waits in memory and opens once signed in.
        if (joinPending) {
            Text(
                text = stringResource(R.string.auth_join_pending),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.infoSurface, GiftiaryShapes.container)
                    .padding(GiftiarySpacing.l),
                style = GiftiaryType.bodySmall,
                color = colors.textPrimary,
            )
        }
        ProviderSignInSection(
            providers = providers,
            state = providerState,
            onProvider = onProvider,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = GiftiarySpacing.s),
        )
        CredentialsFields(
            state = state,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onSubmit = onSubmit,
            showPasswordHint = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            GiftiaryTextAction(
                text = stringResource(R.string.auth_forgot_password),
                onClick = onForgotPassword,
                enabled = !state.isSubmitting && !providerBusy,
            )
        }
        GiftiaryPrimaryButton(
            text = stringResource(R.string.auth_sign_in),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !providerBusy,
            loading = state.isSubmitting,
            trailingArrow = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.auth_new_here), style = GiftiaryType.body, color = colors.textSecondary)
            GiftiaryTextAction(
                text = stringResource(R.string.auth_go_to_create_account),
                onClick = onCreateAccount,
                underline = true,
                enabled = !state.isSubmitting && !providerBusy,
            )
        }
    }
}

@PreviewLightDark
@GiftiaryAdaptivePreviews
@Composable
private fun SignInPreview() {
    GiftiaryTheme {
        SignInContent(
            state = CredentialsUiState(email = "anna@example.com"),
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onCreateAccount = {},
            onForgotPassword = {},
            providers = AuthProvider.entries,
            providerState = ProviderSignInState.Idle,
            onProvider = {},
        )
    }
}
