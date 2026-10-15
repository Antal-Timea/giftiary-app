package app.gifty.presentation.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.gifty.R
import app.gifty.presentation.theme.GiftyShapes
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme

data class ReminderCardActions(
    val onTurnOn: () -> Unit,
    val onTurnOff: () -> Unit,
    val onAllow: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
fun ReminderCard(state: ReminderCardState, actions: ReminderCardActions, modifier: Modifier = Modifier) {
    if (state == ReminderCardState.Hidden) return
    val colors = GiftyTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GiftyShapes.container,
        color = colors.surfaceElevated,
    ) {
        Column(modifier = Modifier.padding(start = GiftySpacing.l, end = GiftySpacing.s, top = GiftySpacing.m, bottom = GiftySpacing.xs)) {
            Text(
                text = stringResource(
                    when (state) {
                        ReminderCardState.On -> R.string.reminder_card_on
                        ReminderCardState.NeedsPermission -> R.string.reminder_card_needs_permission
                        ReminderCardState.Blocked -> R.string.reminder_card_blocked
                        else -> R.string.reminder_card_off
                    },
                ),
                modifier = Modifier.padding(end = GiftySpacing.s),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (state) {
                    ReminderCardState.Off -> CardButton(R.string.reminder_turn_on, actions.onTurnOn)
                    ReminderCardState.On -> CardButton(R.string.reminder_turn_off, actions.onTurnOff)
                    ReminderCardState.NeedsPermission -> {
                        CardButton(R.string.reminder_open_settings, actions.onOpenSettings)
                        CardButton(R.string.reminder_allow, actions.onAllow)
                    }
                    ReminderCardState.Blocked -> CardButton(R.string.reminder_open_settings, actions.onOpenSettings)
                    ReminderCardState.Hidden -> Unit
                }
            }
        }
    }
}

@Composable
private fun CardButton(@StringRes label: Int, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.primaryActionText),
    ) {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}

/** Gifty's own notification settings, where a blocked permission or channel can be turned back on. */
fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
