package app.giftiary.presentation.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

@Composable
fun ForgotPasswordScreen(viewModel: ForgotPasswordViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ForgotPasswordContent(state, viewModel::onEmailChange, viewModel::onSubmit, onBack)
}

/** Asking for a reset link, then saying where it went; set like the approved Sign in board. */
@Composable
internal fun ForgotPasswordContent(
    state: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthPage(modifier) {
        if (state.sent) {
            GiftiaryTaskHeader(
                title = stringResource(R.string.auth_reset_sent_title),
                subtitle = null,
                onBack = onBack,
            )
            AuthStatusText(
                text = stringResource(R.string.auth_reset_sent_message, state.email.trim()),
                color = GiftiaryTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.auth_reset_same_device),
                style = GiftiaryType.bodySmall,
                color = GiftiaryTheme.colors.textSecondary,
            )
            GiftiarySecondaryButton(
                text = stringResource(R.string.auth_back_to_sign_in),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(top = GiftiarySpacing.l),
            )
        } else {
            GiftiaryTaskHeader(
                title = stringResource(R.string.auth_forgot_title),
                subtitle = stringResource(R.string.auth_forgot_message),
                onBack = onBack,
            )
            GiftiaryTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = stringResource(R.string.auth_email),
                modifier = Modifier.fillMaxWidth().padding(top = GiftiarySpacing.s),
                errorMessage = state.emailError?.let { inputErrorMessage(it) },
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            )
            state.authError?.let { AuthStatusText(stringResource(it.messageRes())) }
            GiftiaryPrimaryButton(
                text = stringResource(R.string.auth_send_reset_link),
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().padding(top = GiftiarySpacing.s),
                loading = state.isSubmitting,
                trailingArrow = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ForgotPasswordPreview() {
    GiftiaryTheme {
        ForgotPasswordContent(ForgotPasswordUiState(email = "anna@example.com"), {}, {}, {})
    }
}
