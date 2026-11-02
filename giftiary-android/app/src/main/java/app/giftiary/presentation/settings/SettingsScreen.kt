package app.giftiary.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.BuildConfig
import app.giftiary.R
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryRow
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTextAction
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.home.ReminderCardActions
import app.giftiary.presentation.home.ReminderCardState
import app.giftiary.presentation.home.ReminderCardViewModel
import app.giftiary.presentation.home.rememberReminderCardActions
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

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
            onSignOut = onSignOut,
            onDeleteAccount = viewModel::onDeleteAccount,
            onDeleteAccountDismiss = viewModel::onDeleteAccountDismiss,
            onDeleteAccountConfirm = viewModel::onDeleteAccountConfirm,
        ),
    )
}

class SettingsActions(
    val onBack: (() -> Unit)?,
    val onSignOut: () -> Unit,
    val onDeleteAccount: () -> Unit,
    val onDeleteAccountDismiss: () -> Unit,
    val onDeleteAccountConfirm: () -> Unit,
)

/**
 * The approved Settings board's groups, holding what Giftiary has: occasion reminders, the privacy
 * policy, the version, and the account. The name others see is edited from Profile.
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    email: String?,
    reminderState: ReminderCardState,
    reminderActions: ReminderCardActions,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, containerColor = GiftiaryTheme.colors.background) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .giftiaryReadableWidth()
                .padding(horizontal = GiftiarySpacing.taskGutter, vertical = GiftiarySpacing.browseGutter),
        ) {
            GiftiaryTaskHeader(title = stringResource(R.string.settings_title), subtitle = null, onBack = actions.onBack, eyebrow = null)
            if (reminderState != ReminderCardState.Hidden) {
                Group(stringResource(R.string.settings_notifications))
                ReminderRow(reminderState, reminderActions)
            }
            Group(stringResource(R.string.settings_privacy_data))
            PrivacyPolicyLink()
            Group(stringResource(R.string.settings_about))
            SettingsRow(
                title = stringResource(R.string.settings_version),
                trailing = { Text(text = BuildConfig.VERSION_NAME, style = GiftiaryType.body, color = GiftiaryTheme.colors.textPrimary) },
            )
            Group(stringResource(R.string.settings_account))
            SettingsRow(
                title = stringResource(R.string.auth_sign_out),
                supporting = email?.let { stringResource(R.string.settings_signed_in_as, it) },
                onClick = actions.onSignOut,
            )
            GiftiaryDivider()
            SettingsRow(
                title = stringResource(R.string.settings_delete_account),
                supporting = stringResource(R.string.settings_delete_account_detail),
                onClick = actions.onDeleteAccount,
            )
        }
    }
    state.deleteDialog?.let { DeleteAccountConfirmation(it, actions) }
}

@Composable
private fun Group(title: String) {
    Text(
        text = title.uppercase(),
        modifier = Modifier
            .padding(top = GiftiarySpacing.xxl, bottom = GiftiarySpacing.xs)
            .semantics { heading() },
        style = GiftiaryType.wordmark,
        color = GiftiaryTheme.colors.textLabel,
    )
}

@Composable
private fun SettingsRow(
    title: String,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    GiftiaryRow(
        title = title,
        supporting = supporting?.let { { SettingsSupportingText(it) } },
        trailing = trailing,
        titleStyle = GiftiaryType.actionLarge,
        contentPadding = PaddingValues(vertical = GiftiarySpacing.l),
        onClick = onClick,
    )
}

@Composable
private fun SettingsSupportingText(text: String) {
    Text(text = text, style = GiftiaryType.bodySmall, color = GiftiaryTheme.colors.textSecondary)
}

/**
 * Daily occasion reminders as the board's switch. On means the user asked for them; when Android
 * has not allowed or has switched off notifications, that is said under it with the way to fix it.
 */
@Composable
private fun ReminderRow(state: ReminderCardState, actions: ReminderCardActions) {
    val colors = GiftiaryTheme.colors
    val on = state != ReminderCardState.Off
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = on, role = Role.Switch, onValueChange = { if (it) actions.onTurnOn() else actions.onTurnOff() })
                .padding(vertical = GiftiarySpacing.l),
            horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(R.string.reminder_channel_name), style = GiftiaryType.actionLarge, color = colors.textPrimary)
                SettingsSupportingText(
                    stringResource(
                        when (state) {
                            ReminderCardState.On -> R.string.reminder_card_on
                            ReminderCardState.NeedsPermission -> R.string.reminder_card_needs_permission
                            ReminderCardState.Blocked -> R.string.reminder_card_blocked
                            ReminderCardState.Off, ReminderCardState.Hidden -> R.string.reminder_card_off
                        },
                    ),
                )
            }
            Switch(
                checked = on,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.background,
                    checkedTrackColor = colors.support,
                    checkedBorderColor = colors.support,
                    uncheckedThumbColor = colors.textMuted,
                    uncheckedTrackColor = colors.background,
                    uncheckedBorderColor = colors.borderStrong,
                ),
            )
        }
        if (state == ReminderCardState.NeedsPermission || state == ReminderCardState.Blocked) {
            Row(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
            ) {
                if (state == ReminderCardState.NeedsPermission) {
                    GiftiaryTextAction(text = stringResource(R.string.reminder_allow), onClick = actions.onAllow, underline = true)
                }
                GiftiaryTextAction(text = stringResource(R.string.reminder_open_settings), onClick = actions.onOpenSettings, underline = true)
            }
        }
    }
}

/** Opens the published privacy policy in the browser, in the app's language. */
@Composable
internal fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.settings_privacy_policy_url)
    GiftiaryRow(
        title = stringResource(R.string.settings_privacy_policy),
        modifier = modifier,
        trailing = {
            Icon(
                painter = painterResource(R.drawable.ic_open_link),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = GiftiaryTheme.colors.textLabel,
            )
        },
        titleStyle = GiftiaryType.actionLarge,
        contentPadding = PaddingValues(vertical = GiftiarySpacing.l),
        // Without a browser there is nothing to open it in; that is not an app error.
        onClick = { runCatching { uriHandler.openUri(url) } },
    )
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

@Preview
@Composable
private fun SettingsPreview() {
    GiftiaryTheme {
        SettingsContent(
            state = SettingsUiState(),
            email = "anna@example.com",
            reminderState = ReminderCardState.On,
            reminderActions = ReminderCardActions({}, {}, {}, {}),
            actions = SettingsActions({}, {}, {}, {}, {}),
        )
    }
}
