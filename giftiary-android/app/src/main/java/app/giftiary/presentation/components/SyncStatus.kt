package app.giftiary.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

// The three status registers of the design system (DS-05). Offline and waiting to sync are
// never failures, so none of them uses coral or the word "failed".

/** About the device, under a screen header: "Offline", plus what is queued when anything is. */
@Composable
fun OfflineStatus(waitingChanges: Int, modifier: Modifier = Modifier) {
    StatusPill(
        icon = R.drawable.ic_status_offline,
        title = stringResource(R.string.status_offline),
        detail = if (waitingChanges > 0) {
            pluralStringResource(R.plurals.status_changes_waiting, waitingChanges, waitingChanges)
        } else {
            null
        },
        modifier = modifier,
    )
}

/** About one record, on its detail screen; a synced record shows nothing at all. */
@Composable
fun PendingSyncStatus(modifier: Modifier = Modifier) {
    StatusPill(
        icon = R.drawable.ic_status_pending,
        title = stringResource(R.string.status_waiting_to_sync),
        detail = stringResource(R.string.status_saved_on_device),
        modifier = modifier,
    )
}

/**
 * Above cached content whose refresh failed. Deliberately a plain line rather than a pill: it is a
 * comment on the content, and the connection may be fine.
 */
@Composable
fun RefreshFailedLine(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_status_refresh_failed),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.textLabel,
        )
        Text(
            text = statusText(
                stringResource(R.string.status_refresh_failed),
                stringResource(R.string.status_showing_saved),
                detailWeight = FontWeight.Normal,
            ),
            modifier = Modifier.weight(1f),
            style = GiftiaryType.metadata.copy(fontSize = 12.5.sp),
            color = colors.textLabel,
        )
        GiftiaryTextAction(text = stringResource(R.string.status_try_again), onClick = onRetry)
    }
}

@Composable
private fun StatusPill(@DrawableRes icon: Int, title: String, detail: String?, modifier: Modifier) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .background(colors.supportSurface, GiftiaryShapes.pill)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textPrimary)
        Text(
            text = statusText(title, detail),
            style = GiftiaryType.metadata.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun statusText(title: String, detail: String?, detailWeight: FontWeight = FontWeight.Medium) = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = GiftiaryTheme.colors.textPrimary)) { append(title) }
    if (detail != null) {
        withStyle(SpanStyle(fontWeight = detailWeight, color = GiftiaryTheme.colors.textLabel)) {
            append(" · ")
            append(detail)
        }
    }
}
