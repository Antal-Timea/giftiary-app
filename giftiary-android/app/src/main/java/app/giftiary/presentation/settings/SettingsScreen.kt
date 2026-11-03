package app.giftiary.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import app.giftiary.presentation.theme.GiftiaryShapes
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
        // Deleting the account replaces Settings rather than covering it, so nothing else is in reach.
        state.deleteDialog?.let {
            DeleteAccountPage(it, actions, Modifier.padding(contentPadding))
            return@Scaffold
        }
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

/**
 * Deleting the account, as its own page in place of Settings: what goes, then the one destructive
 * action, outlined in coral so it never reads as a save, and Cancel. Once the deletion runs it cannot
 * be stopped, so back and Cancel are gone while it does. It ends by finishing, when the app leaves
 * Settings, or by failing, which is said here; deleting again then tries again.
 */
@Composable
private fun DeleteAccountPage(dialog: DeleteAccountDialog, actions: SettingsActions, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    // Back cancels, as Cancel does; while the deletion runs the view model ignores it.
    BackHandler(onBack = actions.onDeleteAccountDismiss)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .giftiaryReadableWidth()
            .padding(horizontal = GiftiarySpacing.taskGutter, vertical = GiftiarySpacing.browseGutter),
    ) {
        GiftiaryTaskHeader(
            title = stringResource(R.string.settings_delete_account_title),
            subtitle = null,
            onBack = if (dialog.deleting) null else actions.onDeleteAccountDismiss,
            eyebrow = null,
        )
        Text(
            text = stringResource(R.string.settings_delete_account_message),
            modifier = Modifier.padding(top = GiftiarySpacing.l),
            style = GiftiaryType.body,
            color = colors.textPrimary,
        )
        if (dialog.failure != null) {
            Text(
                text = stringResource(
                    when (dialog.failure) {
                        AccountDeletionFailure.NETWORK -> R.string.sharing_network_error
                        AccountDeletionFailure.SERVER -> R.string.sharing_server_error
                    },
                ),
                modifier = Modifier
                    .padding(top = GiftiarySpacing.l)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = GiftiaryType.body,
                color = colors.primaryActionText,
            )
        }
        DeleteAccountButton(
            deleting = dialog.deleting,
            onClick = actions.onDeleteAccountConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftiarySpacing.xxl),
        )
        if (!dialog.deleting) {
            GiftiaryTextAction(
                text = stringResource(R.string.action_cancel),
                onClick = actions.onDeleteAccountDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GiftiarySpacing.s),
            )
        }
    }
}

@Composable
private fun DeleteAccountButton(deleting: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = !deleting,
        shape = GiftiaryShapes.button,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.primaryActionText,
            disabledContentColor = colors.primaryActionText,
        ),
        border = BorderStroke(1.5.dp, colors.primaryActionText),
        contentPadding = PaddingValues(GiftiarySpacing.l),
    ) {
        if (deleting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.primaryActionText, strokeWidth = 2.dp)
        } else {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(GiftiarySpacing.s))
        Text(text = stringResource(R.string.settings_delete_account), style = GiftiaryType.actionLarge)
    }
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
