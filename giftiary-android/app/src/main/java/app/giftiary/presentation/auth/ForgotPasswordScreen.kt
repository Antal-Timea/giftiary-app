package app.giftiary.presentation.auth

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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

@Composable
fun ForgotPasswordScreen(viewModel: ForgotPasswordViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ForgotPasswordContent(state, viewModel::onEmailChange, viewModel::onSubmit, onBack)
}

@Composable
private fun ForgotPasswordContent(
    state: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = "", onBack = onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.l),
        ) {
            Text(
                text = stringResource(if (state.sent) R.string.auth_reset_sent_title else R.string.auth_forgot_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(GiftiarySpacing.m))
            if (state.sent) {
                Text(
                    text = stringResource(R.string.auth_reset_sent_message, state.email.trim()),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(GiftiarySpacing.m))
                Text(
                    text = stringResource(R.string.auth_reset_same_device),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(GiftiarySpacing.xxl))
                GiftiarySecondaryButton(
                    text = stringResource(R.string.auth_back_to_sign_in),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(R.string.auth_forgot_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(GiftiarySpacing.xxl))
                GiftiaryTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = stringResource(R.string.auth_email),
                    modifier = Modifier.fillMaxWidth(),
                    errorMessage = state.emailError?.let { inputErrorMessage(it) },
                    enabled = !state.isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                )
                state.authError?.let { error ->
                    Spacer(Modifier.height(GiftiarySpacing.m))
                    Text(
                        text = stringResource(error.messageRes()),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error,
                    )
                }
                Spacer(Modifier.height(GiftiarySpacing.xl))
                GiftiaryPrimaryButton(
                    text = stringResource(R.string.auth_send_reset_link),
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    loading = state.isSubmitting,
                )
            }
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
