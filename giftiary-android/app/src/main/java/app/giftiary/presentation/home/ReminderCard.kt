package app.giftiary.presentation.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.giftiary.R
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryTextAction
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

data class ReminderCardActions(
    val onTurnOn: () -> Unit,
    val onTurnOff: () -> Unit,
    val onAllow: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * Daily reminders on Home, kept quiet below Coming up: no raised card, but an uncontained row
 * between hairlines, a calendar mark beside the state in words, and that state's own text actions,
 * underlined as in Settings.
 */
@Composable
fun ReminderCard(state: ReminderCardState, actions: ReminderCardActions, modifier: Modifier = Modifier) {
    if (state == ReminderCardState.Hidden) return
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        GiftiaryDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftiarySpacing.m, bottom = GiftiarySpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.thumbnailFallback, GiftiaryShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textLabel)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        when (state) {
                            ReminderCardState.On -> R.string.reminder_card_on
                            ReminderCardState.NeedsPermission -> R.string.reminder_card_needs_permission
                            ReminderCardState.Blocked -> R.string.reminder_card_blocked
                            else -> R.string.reminder_card_off
                        },
                    ),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = GiftiaryType.body,
                    color = colors.textPrimary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l)) {
                    when (state) {
                        ReminderCardState.Off -> CardAction(R.string.reminder_turn_on, actions.onTurnOn)
                        ReminderCardState.On -> CardAction(R.string.reminder_turn_off, actions.onTurnOff)
                        ReminderCardState.NeedsPermission -> {
                            CardAction(R.string.reminder_allow, actions.onAllow)
                            CardAction(R.string.reminder_open_settings, actions.onOpenSettings)
                        }
                        ReminderCardState.Blocked -> CardAction(R.string.reminder_open_settings, actions.onOpenSettings)
                        ReminderCardState.Hidden -> Unit
                    }
                }
            }
        }
        GiftiaryDivider()
    }
}

@Composable
private fun CardAction(@StringRes label: Int, onClick: () -> Unit) {
    GiftiaryTextAction(text = stringResource(label), onClick = onClick, underline = true)
}

/** Giftiary's own notification settings, where a blocked permission or channel can be turned back on. */
fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/**
 * What the reminder card's buttons do, wherever it is shown. Coming back from the system's
 * notification settings shows what was changed there.
 */
@Composable
fun rememberReminderCardActions(reminders: ReminderCardViewModel): ReminderCardActions {
    LifecycleResumeEffect(reminders) {
        reminders.onResume()
        onPauseOrDispose {}
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        reminders.onPermissionResult()
    }
    val askToNotify = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val context = LocalContext.current
    return ReminderCardActions(
        onTurnOn = { if (reminders.onTurnOn()) askToNotify() },
        onTurnOff = reminders::onTurnOff,
        onAllow = askToNotify,
        onOpenSettings = { context.startActivity(notificationSettingsIntent(context)) },
    )
}
