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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    onCreateAccount: () -> Unit,
    onForgotPassword: (email: String) -> Unit,
    modifier: Modifier = Modifier,
    joinPending: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignInContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onCreateAccount = onCreateAccount,
        onForgotPassword = { onForgotPassword(state.email) },
        joinPending = joinPending,
        modifier = modifier,
    )
}

@Composable
private fun SignInContent(
    state: CredentialsUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
    joinPending: Boolean = false,
) {
    val colors = GiftiaryTheme.colors
    Scaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xxxl),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(GiftiarySpacing.s))
            Text(
                text = stringResource(R.string.auth_sign_in_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(GiftiarySpacing.m))
            Text(
                text = stringResource(R.string.auth_sign_in_message),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
            // An invite opened while signed out waits in memory and opens once signed in.
            if (joinPending) {
                Spacer(Modifier.height(GiftiarySpacing.l))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = GiftiaryShapes.container,
                    color = colors.surfaceElevated,
                ) {
                    Text(
                        text = stringResource(R.string.auth_join_pending),
                        modifier = Modifier.padding(GiftiarySpacing.l),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )
                }
            }
            Spacer(Modifier.height(GiftiarySpacing.xxl))
            CredentialsFields(
                state = state,
                onEmailChange = onEmailChange,
                onPasswordChange = onPasswordChange,
                onSubmit = onSubmit,
                showPasswordHint = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GiftiarySpacing.xl))
            GiftiaryPrimaryButton(
                text = stringResource(R.string.auth_sign_in),
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                loading = state.isSubmitting,
            )
            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
            ) {
                Text(stringResource(R.string.auth_forgot_password))
            }
            Spacer(Modifier.height(GiftiarySpacing.s))
            GiftiarySecondaryButton(
                text = stringResource(R.string.auth_go_to_create_account),
                onClick = onCreateAccount,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
            )
        }
    }
}

@PreviewLightDark
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
        )
    }
}
