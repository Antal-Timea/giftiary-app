package app.giftiary.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionPlan
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.model.SyncStatus
import app.giftiary.domain.model.UpcomingOccasion
import app.giftiary.domain.model.turningAge
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.presentation.components.EditorialHero
import app.giftiary.presentation.components.EditorialImage
import app.giftiary.presentation.components.GiftiaryAdaptivePreviews
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.components.ProvideWidthClass
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.components.giftiaryWideWidth
import app.giftiary.presentation.gifts.GiftMemoryCard
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.people.PersonAvatar
import app.giftiary.presentation.sync.SyncStatusContent
import app.giftiary.presentation.sync.SyncStatusLine
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun HomeScreen(
    syncStatus: SyncStatusContent,
    comingUp: ComingUpContent,
    onOpenComingUp: (occasionId: UUID) -> Unit,
    reminderCard: ReminderCardState,
    reminderActions: ReminderCardActions,
    recentGifts: RecentGiftsContent,
    onOpenGift: (giftId: UUID) -> Unit,
    onOpenAllGifts: () -> Unit,
    onOpenOwnOccasions: () -> Unit,
    onOpenSyncAttention: () -> Unit,
    // The name the profile entry's monogram is drawn from (see ownMonogramName).
    accountName: String?,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    accountAvatar: LoadedPhoto? = null,
    widthClass: WidthClass = LocalWidthClass.current,
    today: LocalDate = LocalDate.now(),
) {
    val comingUpBand: @Composable (Modifier) -> Unit = { bandModifier ->
        ComingUpBand(
            content = comingUp,
            onOpen = { onOpenComingUp(it.occasionId) },
            modifier = bandModifier,
            // Always here, also with nothing coming up: the user's own occasions have no other place.
            footer = { ChevronLink(text = stringResource(R.string.own_occasions_title), onClick = onOpenOwnOccasions) },
        )
    }
    val recentGiftsPart: @Composable (Modifier) -> Unit = { partModifier ->
        RecentGifts(content = recentGifts, onOpenGift = onOpenGift, onOpenAll = onOpenAllGifts, modifier = partModifier)
    }
    // The hero runs up under the status bar, so the screen keeps clear of it by itself.
    Scaffold(modifier = modifier, contentWindowInsets = WindowInsets(0)) { contentPadding ->
        if (widthClass == WidthClass.EXPANDED) {
            WideHome(
                comingUp = comingUpBand,
                recentGifts = recentGiftsPart,
                syncStatus = syncStatus,
                onOpenSyncAttention = onOpenSyncAttention,
                reminderCard = reminderCard,
                reminderActions = reminderActions,
                profileEntry = { ProfileEntry(accountName, accountAvatar, onOpenProfile) },
                modifier = Modifier.padding(contentPadding).windowInsetsPadding(WindowInsets.statusBars),
            )
            return@Scaffold
        }
        // Phones, and medium windows at a readable width.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .giftiaryReadableWidth(),
        ) {
            HomeHero(today = today, profileEntry = { ProfileEntry(accountName, accountAvatar, onOpenProfile) })
            SyncStatusLine(
                content = syncStatus,
                onOpenAttention = onOpenSyncAttention,
                modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = 14.dp),
            )
            comingUpBand(Modifier.padding(top = 14.dp))
            if (reminderCard != ReminderCardState.Hidden) {
                ReminderCard(
                    state = reminderCard,
                    actions = reminderActions,
                    modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.l),
                )
            }
            recentGiftsPart(
                Modifier.padding(
                    start = GiftiarySpacing.browseGutter,
                    end = GiftiarySpacing.browseGutter,
                    top = GiftiarySpacing.browseGutter,
                    bottom = GiftiarySpacing.l,
                ),
            )
        }
    }
}

/**
 * The warm editorial hero: the approved photograph (G1-1), with the wordmark, the question and
 * today's date, and the handwritten line set on it.
 */
@Composable
private fun HomeHero(today: LocalDate, profileEntry: @Composable () -> Unit) {
    val colors = GiftiaryTheme.colors
    EditorialHero(
        modifier = Modifier.fillMaxWidth(),
        image = {
            EditorialImage(R.drawable.giftiary_home_hero)
            // The photograph's flowers sit on the left, under the words: a wash of the hero's own
            // yellow keeps them readable without touching the gift on the right.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to colors.heroFallback.copy(alpha = 0.9f),
                            0.5f to colors.heroFallback.copy(alpha = 0.7f),
                            0.75f to Color.Transparent,
                        ),
                    ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                // At least the approved height; large text makes it taller rather than clipping the date.
                .heightIn(min = HERO_HEIGHT)
                .fillMaxWidth()
                .padding(start = 20.dp, end = GiftiarySpacing.s, top = GiftiarySpacing.s, bottom = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    modifier = Modifier.weight(1f).padding(top = GiftiarySpacing.m),
                    style = GiftiaryType.wordmark,
                    color = colors.textPrimary,
                )
                profileEntry()
            }
            Column(modifier = Modifier.padding(top = 11.dp).widthIn(max = 180.dp)) {
                Text(
                    text = stringResource(R.string.home_title),
                    modifier = Modifier.semantics { heading() },
                    style = GiftiaryType.editorialSection.copy(lineHeight = GiftiaryType.editorialSection.fontSize * 1.26f),
                    color = colors.textPrimary,
                )
                Text(
                    text = today.format(DateTimeFormatter.ofPattern(stringResource(R.string.home_date_pattern), LocalConfiguration.current.locales[0])),
                    modifier = Modifier.padding(top = 7.dp),
                    style = GiftiaryType.metadata.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.home_script),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .rotate(-2f),
                    style = GiftiaryType.handwriting,
                    color = colors.textLabel,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_heart),
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 4.dp).size(15.dp),
                    tint = colors.textLabel,
                )
            }
        }
    }
}

/**
 * The approved hero's profile entry: the account's monogram, opening Profile, where Settings and
 * signing out are. Without a name, say before the profile is read offline, the people glyph shows.
 */
@Composable
private fun ProfileEntry(name: String?, avatar: LoadedPhoto?, onClick: () -> Unit) {
    val label = stringResource(R.string.home_open_profile)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(GiftiaryShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            PersonAvatar(name = name.orEmpty(), photo = avatar, size = 36.dp)
            return@Box
        }
        GiftiaryAvatar(
            name = name.orEmpty(),
            size = 36.dp,
            photo = if (name.isNullOrBlank()) {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_people),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = GiftiaryTheme.colors.textPrimary,
                    )
                }
            } else {
                null
            },
        )
    }
}

/** The two latest gifts as photo cards, with the way into the whole history. */
@Composable
private fun RecentGifts(
    content: RecentGiftsContent,
    onOpenGift: (UUID) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hidden while unread or unreadable: it only adds to Home, and Gifts says what went wrong.
    val loaded = content as? RecentGiftsContent.Loaded ?: return
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_recent_gifts),
                    modifier = Modifier.semantics { heading() },
                    style = GiftiaryType.sectionTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.home_recent_gifts_subtitle),
                    modifier = Modifier.padding(top = 3.dp),
                    style = GiftiaryType.metadata,
                    color = colors.textSecondary,
                )
            }
            if (loaded.gifts.isNotEmpty()) ChevronLink(text = stringResource(R.string.home_see_all), onClick = onOpenAll)
        }
        if (loaded.gifts.isEmpty()) {
            Text(text = stringResource(R.string.home_recent_gifts_empty), style = GiftiaryType.bodySmall, color = colors.textLabel)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                loaded.gifts.forEach { entry -> RecentGiftCard(entry, loaded, onOpenGift, Modifier.weight(1f)) }
                // A single gift keeps the card width of two.
                if (loaded.gifts.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecentGiftCard(entry: GiftEntry, content: RecentGiftsContent.Loaded, onOpenGift: (UUID) -> Unit, modifier: Modifier) {
    GiftMemoryCard(
        entry = entry,
        photo = entry.gift.photoPath?.let { content.photos[it] },
        onClick = { onOpenGift(entry.gift.id) },
        modifier = modifier,
        showOccasion = true,
    )
}

/** A text link that opens another screen; the chevron says so. */
@Composable
private fun ChevronLink(text: String, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = text, style = GiftiaryType.label, color = colors.textPrimary)
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = colors.textPrimary,
        )
    }
}

/**
 * Beside the rail on a wide window: the header across, then what is coming up in the wider
 * column and the reminder card, sync status and recent gifts beside it, each column scrolling on
 * its own. No approved wide board exists, so this keeps the earlier arrangement in the new parts.
 */
@Composable
private fun WideHome(
    comingUp: @Composable (Modifier) -> Unit,
    recentGifts: @Composable (Modifier) -> Unit,
    syncStatus: SyncStatusContent,
    onOpenSyncAttention: () -> Unit,
    reminderCard: ReminderCardState,
    reminderActions: ReminderCardActions,
    profileEntry: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .giftiaryWideWidth()
            .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xxxl),
    ) {
        WideHeader(profileEntry)
        Spacer(Modifier.height(GiftiarySpacing.xxl))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.xxl),
        ) {
            Column(
                modifier = Modifier
                    .weight(3f)
                    .verticalScroll(rememberScrollState()),
            ) {
                comingUp(Modifier)
            }
            Column(
                modifier = Modifier
                    .weight(2f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
            ) {
                ReminderCard(state = reminderCard, actions = reminderActions)
                SyncStatusLine(content = syncStatus, onOpenAttention = onOpenSyncAttention)
                recentGifts(Modifier)
            }
        }
    }
}

@Composable
private fun WideHeader(profileEntry: @Composable () -> Unit) {
    val colors = GiftiaryTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            modifier = Modifier.weight(1f),
            style = GiftiaryType.wordmark,
            color = colors.textSecondary,
        )
        profileEntry()
    }
    Spacer(Modifier.height(GiftiarySpacing.s))
    Text(
        text = stringResource(R.string.home_title),
        modifier = Modifier.semantics { heading() },
        style = GiftiaryType.display,
        color = colors.textPrimary,
    )
}

private val HERO_HEIGHT = 336.dp

@PreviewLightDark
@GiftiaryAdaptivePreviews
@Composable
private fun HomeScreenPreview() {
    GiftiaryTheme {
        ProvideWidthClass {
            HomePreviewContent()
        }
    }
}

@Composable
private fun HomePreviewContent() {
    HomeScreen(
        syncStatus = SyncStatusContent.Loaded(SyncStatus(waitingCount = 3, attention = emptyList())),
        comingUp = ComingUpContent.Loaded(
            listOf(
                previewItem("Anna", OccasionType.BIRTHDAY, daysFromToday = 0, year = 1990, plan = OccasionPlan(ideas = 2, bought = 1)),
                previewItem("Béla", OccasionType.NAME_DAY, daysFromToday = 1, year = null),
                previewItem("Csilla", OccasionType.ANNIVERSARY, daysFromToday = 12, year = 2015),
            ),
        ),
        onOpenComingUp = {},
        reminderCard = ReminderCardState.Off,
        reminderActions = ReminderCardActions({}, {}, {}, {}),
        recentGifts = RecentGiftsContent.Loaded(emptyList()),
        onOpenGift = {},
        onOpenAllGifts = {},
        onOpenOwnOccasions = {},
        onOpenSyncAttention = {},
        accountName = "Anna",
        onOpenProfile = {},
    )
}

private fun previewItem(name: String, type: OccasionType, daysFromToday: Int, year: Int?, plan: OccasionPlan? = null): ComingUpItem {
    val date = LocalDate.of(2026, 5, 10).plusDays(daysFromToday.toLong())
    val person = Person(UUID.randomUUID(), name, null, null, null, Instant.EPOCH, Instant.EPOCH)
    val occasion = Occasion(UUID.randomUUID(), person.id, type, null, date.dayOfMonth, date.monthValue, year, true, Instant.EPOCH, Instant.EPOCH)
    return ComingUpItem(UpcomingOccasion(occasion, person, date, daysFromToday, turningAge(occasion, date)), plan)
}
