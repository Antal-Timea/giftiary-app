package app.giftiary.presentation.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.data.auth.ProviderSignInState
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.domain.usecase.CredentialsInputError
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

@Composable
fun CreateAccountScreen(
    viewModel: CreateAccountViewModel,
    onBack: () -> Unit,
    onConfirmationSent: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    providerViewModel: ProviderSignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val providerState by providerViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.confirmationSentTo) {
        state.confirmationSentTo?.let(onConfirmationSent)
    }
    CreateAccountContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onPasswordConfirmationChange = viewModel::onPasswordConfirmationChange,
        onSubmit = viewModel::submit,
        onBack = onBack,
        providers = providerViewModel.providers,
        providerState = providerState,
        onProvider = rememberProviderClick(providerViewModel),
        modifier = modifier,
    )
}

/**
 * Account creation, set like the approved Sign in board: a provider creates the account on first
 * use, so its buttons lead here too, before the email form.
 */
@Composable
internal fun CreateAccountContent(
    state: CreateAccountUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmationChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    providers: List<AuthProvider>,
    providerState: ProviderSignInState,
    onProvider: (AuthProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthPage(modifier) {
        GiftiaryTaskHeader(
            title = stringResource(R.string.auth_create_account_title),
            subtitle = stringResource(R.string.auth_create_account_message),
            onBack = onBack,
        )
        ProviderSignInSection(
            providers = providers,
            state = providerState,
            onProvider = onProvider,
            enabled = !state.credentials.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = GiftiarySpacing.s),
        )
        CredentialsFields(
            state = state.credentials,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onSubmit = onSubmit,
            showPasswordHint = true,
            modifier = Modifier.fillMaxWidth(),
            passwordConfirmation = state.passwordConfirmation,
            confirmationError = state.confirmationError,
            onPasswordConfirmationChange = onPasswordConfirmationChange,
        )
        GiftiaryPrimaryButton(
            text = stringResource(R.string.auth_create_account),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().padding(top = GiftiarySpacing.s),
            enabled = !providerState.isBusy,
            loading = state.credentials.isSubmitting,
            trailingArrow = true,
        )
    }
}

@PreviewLightDark
@Composable
private fun CreateAccountPreview() {
    GiftiaryTheme {
        CreateAccountContent(
            state = CreateAccountUiState(
                credentials = CredentialsUiState(email = "anna@example.com", password = "long enough"),
                passwordConfirmation = "long enuogh",
                confirmationError = CredentialsInputError.PASSWORDS_DO_NOT_MATCH,
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onPasswordConfirmationChange = {},
            onSubmit = {},
            onBack = {},
            providers = AuthProvider.entries,
            providerState = ProviderSignInState.Idle,
            onProvider = {},
        )
    }
}
