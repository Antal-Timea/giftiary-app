package app.giftiary.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.giftiary.R
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.usecase.CredentialsInputError
import app.giftiary.domain.usecase.MIN_PASSWORD_LENGTH
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/**
 * Email and password, plus the backend's answer to the last attempt, shared by both auth forms.
 * A non-null [passwordConfirmation] adds the repeat-password field that account creation needs.
 */
@Composable
internal fun CredentialsFields(
    state: CredentialsUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    showPasswordHint: Boolean,
    modifier: Modifier = Modifier,
    passwordConfirmation: String? = null,
    confirmationError: CredentialsInputError? = null,
    onPasswordConfirmationChange: (String) -> Unit = {},
) {
    val submitOnDone = KeyboardActions(onDone = { onSubmit() })
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l)) {
        GiftiaryTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.auth_email),
            modifier = Modifier.fillMaxWidth(),
            errorMessage = state.emailError?.let { inputErrorMessage(it) },
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        GiftiaryTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.auth_password),
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (showPasswordHint) pluralStringResource(R.plurals.auth_password_hint, MIN_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH) else null,
            errorMessage = state.passwordError?.let { inputErrorMessage(it) },
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (passwordConfirmation == null) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = submitOnDone,
            visualTransformation = PasswordVisualTransformation(),
        )
        if (passwordConfirmation != null) {
            GiftiaryTextField(
                value = passwordConfirmation,
                onValueChange = onPasswordConfirmationChange,
                label = stringResource(R.string.auth_password_confirmation),
                modifier = Modifier.fillMaxWidth(),
                errorMessage = confirmationError?.let { inputErrorMessage(it) },
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = submitOnDone,
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        state.authError?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = GiftiaryTheme.colors.error,
            )
        }
    }
}

@Composable
internal fun inputErrorMessage(error: CredentialsInputError): String = when (error) {
    CredentialsInputError.EMAIL_REQUIRED -> stringResource(R.string.auth_error_email_required)
    CredentialsInputError.EMAIL_INVALID -> stringResource(R.string.auth_error_email_invalid)
    CredentialsInputError.PASSWORD_REQUIRED -> stringResource(R.string.auth_error_password_required)
    CredentialsInputError.PASSWORD_TOO_SHORT -> pluralStringResource(
        R.plurals.auth_error_password_too_short,
        MIN_PASSWORD_LENGTH,
        MIN_PASSWORD_LENGTH,
    )
    CredentialsInputError.CONFIRMATION_REQUIRED -> stringResource(R.string.auth_error_confirmation_required)
    CredentialsInputError.PASSWORDS_DO_NOT_MATCH -> stringResource(R.string.auth_error_passwords_do_not_match)
}

internal fun AuthError.messageRes(): Int = when (this) {
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthError.EMAIL_ALREADY_REGISTERED -> R.string.auth_error_email_already_registered
    AuthError.EMAIL_NOT_CONFIRMED -> R.string.auth_error_email_not_confirmed
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.INVALID_EMAIL -> R.string.auth_error_invalid_email
    AuthError.NETWORK_UNAVAILABLE -> R.string.auth_error_network
    AuthError.RATE_LIMITED -> R.string.auth_error_rate_limited
    AuthError.LINK_INVALID -> R.string.auth_error_link_invalid
    AuthError.SAME_PASSWORD -> R.string.auth_error_same_password
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}
