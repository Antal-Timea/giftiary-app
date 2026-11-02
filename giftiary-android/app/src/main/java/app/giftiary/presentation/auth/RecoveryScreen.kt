package app.giftiary.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.data.auth.RecoveryFailure
import app.giftiary.data.auth.RecoveryState
import app.giftiary.domain.usecase.MIN_PASSWORD_LENGTH
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTextAction
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/** Shown instead of the app while a password recovery is under way. */
@Composable
fun RecoveryScreen(viewModel: RecoveryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    RecoveryContent(
        state = state,
        input = input,
        actions = RecoveryActions(
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmationChange = viewModel::onConfirmationChange,
            onSubmit = viewModel::onSubmit,
            onRetry = viewModel::onRetry,
            onLeave = viewModel::onLeave,
            onSignOutOtherAccount = viewModel::onSignOutOtherAccount,
        ),
        modifier = modifier,
    )
}

class RecoveryActions(
    val onPasswordChange: (String) -> Unit,
    val onConfirmationChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onRetry: () -> Unit,
    val onLeave: () -> Unit,
    val onSignOutOtherAccount: () -> Unit,
)

@Composable
internal fun RecoveryContent(state: RecoveryState, input: NewPasswordInput, actions: RecoveryActions, modifier: Modifier = Modifier) {
    when (state) {
        RecoveryState.Idle, RecoveryState.Checking, RecoveryState.Exchanging, RecoveryState.Leaving ->
            LoadingContent(modifier.background(GiftiaryTheme.colors.background))
        is RecoveryState.ChoosingPassword -> NewPassword(input, state, busy = false, actions, modifier)
        RecoveryState.Updating -> NewPassword(input, RecoveryState.ChoosingPassword(), busy = true, actions, modifier)
        is RecoveryState.Failed -> RecoveryFailed(state.reason, actions, modifier)
    }
}

@Composable
private fun NewPassword(
    input: NewPasswordInput,
    state: RecoveryState.ChoosingPassword,
    busy: Boolean,
    actions: RecoveryActions,
    modifier: Modifier = Modifier,
) {
    AuthPage(modifier) {
        GiftiaryTaskHeader(
            title = stringResource(R.string.auth_new_password_title),
            subtitle = stringResource(R.string.auth_new_password_message),
            onBack = null,
        )
        GiftiaryTextField(
            value = input.password,
            onValueChange = actions.onPasswordChange,
            label = stringResource(R.string.auth_new_password),
            modifier = Modifier.fillMaxWidth().padding(top = GiftiarySpacing.s),
            placeholder = pluralStringResource(R.plurals.auth_password_hint, MIN_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH),
            errorMessage = input.passwordError?.let { inputErrorMessage(it) },
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            visualTransformation = PasswordVisualTransformation(),
        )
        GiftiaryTextField(
            value = input.confirmation,
            onValueChange = actions.onConfirmationChange,
            label = stringResource(R.string.auth_password_confirmation),
            modifier = Modifier.fillMaxWidth(),
            errorMessage = input.confirmationError?.let { inputErrorMessage(it) },
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
            visualTransformation = PasswordVisualTransformation(),
        )
        state.error?.let { AuthStatusText(stringResource(it.messageRes())) }
        GiftiaryPrimaryButton(
            text = stringResource(R.string.auth_save_new_password),
            onClick = actions.onSubmit,
            modifier = Modifier.fillMaxWidth().padding(top = GiftiarySpacing.s),
            loading = busy,
        )
        GiftiaryTextAction(
            text = stringResource(R.string.auth_cancel_recovery),
            onClick = actions.onLeave,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enabled = !busy,
        )
    }
}

@Composable
private fun RecoveryFailed(reason: RecoveryFailure, actions: RecoveryActions, modifier: Modifier = Modifier) {
    AuthPage(modifier) {
        GiftiaryTaskHeader(
            title = stringResource(
                when (reason) {
                    RecoveryFailure.LINK_INVALID -> R.string.auth_recovery_link_invalid_title
                    RecoveryFailure.NETWORK -> R.string.auth_recovery_network_title
                    RecoveryFailure.SIGNED_IN -> R.string.auth_recovery_signed_in_title
                },
            ),
            subtitle = null,
            onBack = null,
        )
        AuthStatusText(
            text = stringResource(
                when (reason) {
                    RecoveryFailure.LINK_INVALID -> R.string.auth_recovery_link_invalid_message
                    RecoveryFailure.NETWORK -> R.string.auth_error_network
                    RecoveryFailure.SIGNED_IN -> R.string.auth_recovery_signed_in_message
                },
            ),
            color = GiftiaryTheme.colors.textPrimary,
        )
        val buttons = Modifier.fillMaxWidth()
        when (reason) {
            RecoveryFailure.LINK_INVALID -> GiftiaryPrimaryButton(
                text = stringResource(R.string.auth_recovery_ok),
                onClick = actions.onLeave,
                modifier = buttons.padding(top = GiftiarySpacing.l),
            )
            RecoveryFailure.NETWORK -> {
                GiftiaryPrimaryButton(
                    text = stringResource(R.string.sharing_retry),
                    onClick = actions.onRetry,
                    modifier = buttons.padding(top = GiftiarySpacing.l),
                )
                GiftiarySecondaryButton(
                    text = stringResource(R.string.auth_cancel_recovery),
                    onClick = actions.onLeave,
                    modifier = buttons,
                )
            }
            RecoveryFailure.SIGNED_IN -> {
                GiftiaryPrimaryButton(
                    text = stringResource(R.string.auth_sign_out),
                    onClick = actions.onSignOutOtherAccount,
                    modifier = buttons.padding(top = GiftiarySpacing.l),
                )
                GiftiarySecondaryButton(
                    text = stringResource(R.string.auth_recovery_stay_signed_in),
                    onClick = actions.onLeave,
                    modifier = buttons,
                )
            }
        }
    }
}

/** After a completed recovery, once; the account opens underneath meanwhile. */
@Composable
fun PasswordChangedScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    AuthPage(modifier) {
        GiftiaryTaskHeader(
            title = stringResource(R.string.auth_password_changed_title),
            subtitle = stringResource(R.string.auth_password_changed_message),
            onBack = null,
        )
        GiftiaryPrimaryButton(
            text = stringResource(R.string.auth_continue),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().padding(top = GiftiarySpacing.l),
            trailingArrow = true,
        )
    }
}

@PreviewLightDark
@Composable
private fun NewPasswordPreview() {
    GiftiaryTheme {
        RecoveryContent(
            state = RecoveryState.ChoosingPassword(),
            input = NewPasswordInput(),
            actions = RecoveryActions({}, {}, {}, {}, {}, {}),
        )
    }
}

@PreviewLightDark
@Composable
private fun RecoveryFailedPreview() {
    GiftiaryTheme {
        RecoveryContent(
            state = RecoveryState.Failed(RecoveryFailure.LINK_INVALID),
            input = NewPasswordInput(),
            actions = RecoveryActions({}, {}, {}, {}, {}, {}),
        )
    }
}
