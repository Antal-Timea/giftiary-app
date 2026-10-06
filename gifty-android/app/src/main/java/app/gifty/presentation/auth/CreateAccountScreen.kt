package app.gifty.presentation.auth

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.usecase.CredentialsInputError
import app.gifty.presentation.components.GiftyPrimaryButton
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme

@Composable
fun CreateAccountScreen(
    viewModel: CreateAccountViewModel,
    onBack: () -> Unit,
    onConfirmationSent: (email: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { GiftyTopBar(title = stringResource(R.string.auth_create_account_title), onBack = onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GiftySpacing.xl, vertical = GiftySpacing.l),
        ) {
            Text(
                text = stringResource(R.string.auth_create_account_message),
                style = MaterialTheme.typography.bodyLarge,
                color = GiftyTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(GiftySpacing.xl))
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
            Spacer(Modifier.height(GiftySpacing.xl))
            GiftyPrimaryButton(
                text = stringResource(R.string.auth_create_account),
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                loading = state.credentials.isSubmitting,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun CreateAccountPreview() {
    GiftyTheme {
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
        )
    }
}
