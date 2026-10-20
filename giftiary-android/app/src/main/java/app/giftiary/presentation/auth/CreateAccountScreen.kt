package app.giftiary.presentation.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.giftiaryReadableWidth
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

@Composable
private fun CreateAccountContent(
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
    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = stringResource(R.string.auth_create_account_title), onBack = onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .giftiaryReadableWidth()
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.l),
        ) {
            Text(
                text = stringResource(R.string.auth_create_account_message),
                style = MaterialTheme.typography.bodyLarge,
                color = GiftiaryTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(GiftiarySpacing.xl))
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
            Spacer(Modifier.height(GiftiarySpacing.xl))
            GiftiaryPrimaryButton(
                text = stringResource(R.string.auth_create_account),
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !providerState.isBusy,
                loading = state.credentials.isSubmitting,
            )
            Spacer(Modifier.height(GiftiarySpacing.xl))
            // A provider creates the account on first use, so its buttons serve here too.
            ProviderSignInSection(
                providers = providers,
                state = providerState,
                onProvider = onProvider,
                enabled = !state.credentials.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
