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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.giftiaryReadableWidth
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
private fun RecoveryContent(state: RecoveryState, input: NewPasswordInput, actions: RecoveryActions, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state) {
            RecoveryState.Idle, RecoveryState.Checking, RecoveryState.Exchanging, RecoveryState.Leaving ->
                LoadingContent(contentModifier)
            is RecoveryState.ChoosingPassword -> NewPassword(input, state, busy = false, actions, contentModifier)
            RecoveryState.Updating -> NewPassword(input, RecoveryState.ChoosingPassword(), busy = true, actions, contentModifier)
            is RecoveryState.Failed -> RecoveryFailed(state.reason, actions, contentModifier)
        }
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
    val colors = GiftiaryTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .giftiaryReadableWidth()
            .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xxxl),
    ) {
        Title(stringResource(R.string.auth_new_password_title))
        Spacer(Modifier.height(GiftiarySpacing.m))
        Text(
            text = stringResource(R.string.auth_new_password_message),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(GiftiarySpacing.xxl))
        GiftiaryTextField(
            value = input.password,
            onValueChange = actions.onPasswordChange,
            label = stringResource(R.string.auth_new_password),
            modifier = Modifier.fillMaxWidth(),
            placeholder = pluralStringResource(R.plurals.auth_password_hint, MIN_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH),
            errorMessage = input.passwordError?.let { inputErrorMessage(it) },
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(GiftiarySpacing.l))
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
        state.error?.let { error ->
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
            text = stringResource(R.string.auth_save_new_password),
            onClick = actions.onSubmit,
            modifier = Modifier.fillMaxWidth(),
            loading = busy,
        )
        Spacer(Modifier.height(GiftiarySpacing.m))
        GiftiarySecondaryButton(
            text = stringResource(R.string.auth_cancel_recovery),
            onClick = actions.onLeave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
    }
}

@Composable
private fun RecoveryFailed(reason: RecoveryFailure, actions: RecoveryActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .giftiaryReadableWidth()
            .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xxxl),
    ) {
        Spacer(Modifier.weight(1f))
        Title(
            stringResource(
                when (reason) {
                    RecoveryFailure.LINK_INVALID -> R.string.auth_recovery_link_invalid_title
                    RecoveryFailure.NETWORK -> R.string.auth_recovery_network_title
                    RecoveryFailure.SIGNED_IN -> R.string.auth_recovery_signed_in_title
                },
            ),
        )
        Spacer(Modifier.height(GiftiarySpacing.l))
        Text(
            text = stringResource(
                when (reason) {
                    RecoveryFailure.LINK_INVALID -> R.string.auth_recovery_link_invalid_message
                    RecoveryFailure.NETWORK -> R.string.auth_error_network
                    RecoveryFailure.SIGNED_IN -> R.string.auth_recovery_signed_in_message
                },
            ),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
            color = GiftiaryTheme.colors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        when (reason) {
            RecoveryFailure.LINK_INVALID -> GiftiaryPrimaryButton(
                text = stringResource(R.string.auth_recovery_ok),
                onClick = actions.onLeave,
                modifier = Modifier.fillMaxWidth(),
            )
            RecoveryFailure.NETWORK -> {
                GiftiaryPrimaryButton(
                    text = stringResource(R.string.sharing_retry),
                    onClick = actions.onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(GiftiarySpacing.m))
                GiftiarySecondaryButton(
                    text = stringResource(R.string.auth_cancel_recovery),
                    onClick = actions.onLeave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RecoveryFailure.SIGNED_IN -> {
                GiftiaryPrimaryButton(
                    text = stringResource(R.string.auth_sign_out),
                    onClick = actions.onSignOutOtherAccount,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(GiftiarySpacing.m))
                GiftiarySecondaryButton(
                    text = stringResource(R.string.auth_recovery_stay_signed_in),
                    onClick = actions.onLeave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** After a completed recovery, once; the account opens underneath meanwhile. */
@Composable
fun PasswordChangedScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .giftiaryReadableWidth()
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xxxl),
        ) {
            Spacer(Modifier.weight(1f))
            Title(stringResource(R.string.auth_password_changed_title))
            Spacer(Modifier.height(GiftiarySpacing.l))
            Text(
                text = stringResource(R.string.auth_password_changed_message),
                style = MaterialTheme.typography.bodyLarge,
                color = GiftiaryTheme.colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            GiftiaryPrimaryButton(
                text = stringResource(R.string.auth_continue),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.displaySmall,
        color = GiftiaryTheme.colors.textPrimary,
    )
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
