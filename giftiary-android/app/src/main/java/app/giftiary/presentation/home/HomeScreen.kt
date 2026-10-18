package app.giftiary.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.giftiary.R
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.model.SyncStatus
import app.giftiary.domain.model.UpcomingOccasion
import app.giftiary.domain.model.turningAge
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.sync.SyncStatusContent
import app.giftiary.presentation.sync.SyncStatusLine
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable
fun HomeScreen(
    syncStatus: SyncStatusContent,
    comingUp: ComingUpContent,
    onOpenComingUp: (occasionId: UUID) -> Unit,
    reminderCard: ReminderCardState,
    reminderActions: ReminderCardActions,
    onOpenGifts: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenWishlists: () -> Unit,
    onOpenOwnOccasions: () -> Unit,
    onOpenSyncAttention: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    Scaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xxxl),
        ) {
            // Only the upper part scrolls, so the actions stay in reach however much is coming up.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(GiftiarySpacing.s))
                Text(
                    text = stringResource(R.string.home_tagline),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(GiftiarySpacing.xxl))
                ComingUpSection(content = comingUp, onOpen = onOpenComingUp)
                // Always here, also with nothing coming up: the user's own occasions have no other place.
                TextButton(
                    onClick = onOpenOwnOccasions,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
                ) {
                    Text(text = stringResource(R.string.own_occasions_title), style = MaterialTheme.typography.labelLarge)
                }
                if (reminderCard != ReminderCardState.Hidden) {
                    Spacer(Modifier.height(GiftiarySpacing.l))
                    ReminderCard(state = reminderCard, actions = reminderActions)
                }
            }
            Spacer(Modifier.height(GiftiarySpacing.l))
            SyncStatusLine(content = syncStatus, onOpenAttention = onOpenSyncAttention)
            Spacer(Modifier.height(GiftiarySpacing.l))
            GiftiaryPrimaryButton(
                text = stringResource(R.string.home_open_gifts),
                onClick = onOpenGifts,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GiftiarySpacing.m))
            GiftiarySecondaryButton(
                text = stringResource(R.string.home_open_people),
                onClick = onOpenPeople,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GiftiarySpacing.m))
            GiftiarySecondaryButton(
                text = stringResource(R.string.home_open_wishlists),
                onClick = onOpenWishlists,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GiftiarySpacing.s))
            // Sign-out lives in Settings now, with the profile and reminders.
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
            ) {
                Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreview() {
    GiftiaryTheme {
        HomeScreen(
            syncStatus = SyncStatusContent.Loaded(SyncStatus(waitingCount = 3, attention = emptyList())),
            comingUp = ComingUpContent.Loaded(
                listOf(
                    previewItem("Anna", OccasionType.BIRTHDAY, daysFromToday = 0, year = 1990),
                    previewItem("Béla", OccasionType.NAME_DAY, daysFromToday = 1, year = null),
                    previewItem("Csilla", OccasionType.ANNIVERSARY, daysFromToday = 12, year = 2015),
                ),
            ),
            onOpenComingUp = {},
            reminderCard = ReminderCardState.Off,
            reminderActions = ReminderCardActions({}, {}, {}, {}),
            onOpenGifts = {},
            onOpenPeople = {},
            onOpenWishlists = {},
            onOpenOwnOccasions = {},
            onOpenSyncAttention = {},
            onOpenSettings = {},
        )
    }
}

private fun previewItem(name: String, type: OccasionType, daysFromToday: Int, year: Int?): ComingUpItem {
    val date = LocalDate.of(2026, 5, 10).plusDays(daysFromToday.toLong())
    val person = Person(UUID.randomUUID(), name, null, null, null, Instant.EPOCH, Instant.EPOCH)
    val occasion = Occasion(UUID.randomUUID(), person.id, type, null, date.dayOfMonth, date.monthValue, year, true, Instant.EPOCH, Instant.EPOCH)
    return ComingUpItem(UpcomingOccasion(occasion, person, date, daysFromToday, turningAge(occasion, date)))
}
