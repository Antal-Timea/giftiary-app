package app.giftiary.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.home.ReminderCard
import app.giftiary.presentation.home.ReminderCardActions
import app.giftiary.presentation.home.ReminderCardState
import app.giftiary.presentation.home.ReminderCardViewModel
import app.giftiary.presentation.home.rememberReminderCardActions
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    reminders: ReminderCardViewModel,
    onBack: (() -> Unit)?,
    onSignOut: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reminderState by reminders.settingsState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        email = viewModel.email,
        reminderState = reminderState,
        reminderActions = rememberReminderCardActions(reminders),
        actions = SettingsActions(
            onBack = onBack,
            onNameChange = viewModel::onNameChange,
            onSave = viewModel::onSave,
            onRetry = viewModel::load,
            onSignOut = onSignOut,
            onMessageShown = viewModel::onMessageShown,
            onDeleteAccount = viewModel::onDeleteAccount,
            onDeleteAccountDismiss = viewModel::onDeleteAccountDismiss,
            onDeleteAccountConfirm = viewModel::onDeleteAccountConfirm,
        ),
    )
}

class SettingsActions(
    val onBack: (() -> Unit)?,
    val onNameChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onRetry: () -> Unit,
    val onSignOut: () -> Unit,
    val onMessageShown: () -> Unit,
    val onDeleteAccount: () -> Unit,
    val onDeleteAccountDismiss: () -> Unit,
    val onDeleteAccountConfirm: () -> Unit,
)

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    email: String?,
    reminderState: ReminderCardState,
    reminderActions: ReminderCardActions,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = when {
        state.saved -> stringResource(R.string.settings_name_saved)
        state.saveFailure == ProfileFailure.NETWORK -> stringResource(R.string.sharing_network_error)
        state.saveFailure != null -> stringResource(R.string.sharing_server_error)
        else -> null
    }
    LaunchedEffect(state.saved, state.saveFailure) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            actions.onMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = stringResource(R.string.settings_title), onBack = actions.onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .giftiaryReadableWidth()
                .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.m),
            verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
        ) {
            Heading(stringResource(R.string.settings_profile))
            Profile(state, actions)
            if (reminderState != ReminderCardState.Hidden) {
                Heading(stringResource(R.string.settings_reminders))
                ReminderCard(state = reminderState, actions = reminderActions)
            }
            Heading(stringResource(R.string.settings_account))
            if (email != null) {
                Text(
                    text = stringResource(R.string.settings_signed_in_as, email),
                    style = MaterialTheme.typography.bodyLarge,
                    color = GiftiaryTheme.colors.textSecondary,
                )
            }
            GiftiarySecondaryButton(
                text = stringResource(R.string.auth_sign_out),
                onClick = actions.onSignOut,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = actions.onDeleteAccount,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.primaryActionText),
            ) {
                Text(stringResource(R.string.settings_delete_account))
            }
        }
    }
    state.deleteDialog?.let { DeleteAccountConfirmation(it, actions) }
}

@Composable
private fun DeleteAccountConfirmation(dialog: DeleteAccountDialog, actions: SettingsActions) {
    val colors = GiftiaryTheme.colors
    AlertDialog(
        onDismissRequest = actions.onDeleteAccountDismiss,
        // Neither back nor a tap outside may abandon a deletion that is running.
        properties = DialogProperties(dismissOnBackPress = !dialog.deleting, dismissOnClickOutside = !dialog.deleting),
        title = { Text(stringResource(R.string.settings_delete_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.m)) {
                Text(stringResource(R.string.settings_delete_account_message))
                if (dialog.failure != null) {
                    Text(
                        text = stringResource(
                            when (dialog.failure) {
                                AccountDeletionFailure.NETWORK -> R.string.sharing_network_error
                                AccountDeletionFailure.SERVER -> R.string.sharing_server_error
                            },
                        ),
                        color = colors.primaryActionText,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions.onDeleteAccountConfirm,
                enabled = !dialog.deleting,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
            ) {
                if (dialog.deleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.primaryAction, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.settings_delete_account))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = actions.onDeleteAccountDismiss,
                enabled = !dialog.deleting,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
    )
}

@Composable
private fun Profile(state: SettingsUiState, actions: SettingsActions) {
    val colors = GiftiaryTheme.colors
    when (val profile = state.profile) {
        ProfileContent.Loading -> CircularProgressIndicator(color = colors.primaryAction)
        is ProfileContent.Failed -> {
            Text(
                text = stringResource(
                    when (profile.failure) {
                        ProfileFailure.UNAVAILABLE -> R.string.settings_profile_unavailable
                        ProfileFailure.NETWORK -> R.string.sharing_network_error
                        ProfileFailure.SERVER -> R.string.sharing_server_error
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
            GiftiarySecondaryButton(text = stringResource(R.string.sharing_retry), onClick = actions.onRetry)
        }
        is ProfileContent.Loaded -> {
            GiftiaryTextField(
                value = state.nameInput,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.settings_display_name),
                modifier = Modifier.fillMaxWidth(),
                errorMessage = if (state.inputInvalid) stringResource(R.string.settings_display_name_invalid) else null,
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onSave() }),
            )
            Text(
                text = stringResource(R.string.settings_display_name_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            GiftiaryPrimaryButton(
                text = stringResource(R.string.settings_save_name),
                onClick = actions.onSave,
                modifier = Modifier.fillMaxWidth(),
                loading = state.isSaving,
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(top = GiftiarySpacing.l)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = GiftiaryTheme.colors.textPrimary,
    )
}

@PreviewLightDark
@Composable
private fun SettingsPreview() {
    GiftiaryTheme {
        SettingsContent(
            state = SettingsUiState(profile = ProfileContent.Loaded(saved = "Anna"), nameInput = "Anna"),
            email = "anna@example.com",
            reminderState = ReminderCardState.Off,
            reminderActions = ReminderCardActions({}, {}, {}, {}),
            actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
