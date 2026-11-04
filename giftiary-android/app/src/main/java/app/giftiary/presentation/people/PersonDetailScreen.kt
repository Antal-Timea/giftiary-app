package app.giftiary.presentation.people

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionPlan
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.model.Wishlist
import app.giftiary.domain.model.nextOccurrence
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.usecase.occasionPlans
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryPillButton
import app.giftiary.presentation.components.HeroCircleButton
import app.giftiary.presentation.components.ListRow
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.components.monogramInitial
import app.giftiary.presentation.giftideas.GiftIdeaRow
import app.giftiary.presentation.gifts.GiftMemoryCard
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.home.DaysCountdown
import app.giftiary.presentation.home.PlanLine
import app.giftiary.presentation.occasions.OccasionRow
import app.giftiary.presentation.occasions.occasionDayMonth
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Composable
fun PersonDetailScreen(
    viewModel: PersonDetailViewModel,
    // Null beside the People list, which stays in view instead.
    onBack: (() -> Unit)?,
    onEditPerson: (UUID) -> Unit,
    onAddOccasion: (UUID) -> Unit,
    onEditOccasion: (UUID) -> Unit,
    onPlanGift: (UUID) -> Unit,
    onAddGift: (UUID) -> Unit,
    onOpenGift: (UUID) -> Unit,
    onAddGiftIdea: (UUID) -> Unit,
    onOpenGiftIdea: (UUID) -> Unit,
    onAddWishlist: (UUID) -> Unit,
    onOpenWishlist: (UUID) -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val giftPhotos by viewModel.giftPhotos.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()
    LaunchedEffect(deletion.deleted) {
        if (deletion.deleted) onDeleted()
    }
    PersonDetailScreen(
        state = state,
        giftPhotos = giftPhotos,
        deletion = deletion,
        onBack = onBack,
        onEditPerson = { onEditPerson(it.id) },
        onAddOccasion = { onAddOccasion(it.id) },
        onEditOccasion = { onEditOccasion(it.id) },
        onPlanGift = { onPlanGift(it.id) },
        onAddGift = { onAddGift(it.id) },
        onOpenGift = { onOpenGift(it.gift.id) },
        onAddGiftIdea = { onAddGiftIdea(it.id) },
        onOpenGiftIdea = { onOpenGiftIdea(it.idea.id) },
        onAddWishlist = { onAddWishlist(it.id) },
        onOpenWishlist = { onOpenWishlist(it.wishlist.id) },
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onDeleteErrorShown = viewModel::onDeleteErrorShown,
    )
}

/**
 * A person: who they are, their next occasion and how far its planning is, the ideas for them,
 * their occasions and wishlists, and the gifts shared with them as memories.
 */
@Composable
fun PersonDetailScreen(
    state: PersonDetailUiState,
    onBack: (() -> Unit)?,
    onEditPerson: (Person) -> Unit,
    onAddOccasion: (Person) -> Unit,
    onEditOccasion: (Occasion) -> Unit,
    onPlanGift: (Occasion) -> Unit,
    onAddGift: (Person) -> Unit,
    onOpenGift: (GiftEntry) -> Unit,
    onAddGiftIdea: (Person) -> Unit,
    onOpenGiftIdea: (GiftIdeaEntry) -> Unit,
    onAddWishlist: (Person) -> Unit,
    onOpenWishlist: (PersonWishlist) -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    giftPhotos: Map<String, LoadedPhoto> = emptyMap(),
    deletion: PersonDeletion = PersonDeletion(),
    today: LocalDate = LocalDate.now(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    val deleteFailedMessage = stringResource(R.string.people_delete_failed)
    LaunchedEffect(deletion.failed) {
        if (deletion.failed) {
            snackbarHostState.showSnackbar(deleteFailedMessage)
            onDeleteErrorShown()
        }
    }
    // The hero runs up under the status bar; the other states keep clear of it themselves.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val messageModifier = Modifier
            .padding(contentPadding)
            .windowInsetsPadding(WindowInsets.statusBars)
        when (state) {
            PersonDetailUiState.Loading -> LoadingContent(messageModifier)
            PersonDetailUiState.NotFound -> MessageContent(stringResource(R.string.person_editor_not_found), messageModifier)
            PersonDetailUiState.LoadFailed -> MessageContent(stringResource(R.string.person_editor_load_failed), messageModifier)
            is PersonDetailUiState.Loaded -> PersonDetailContent(
                state = state,
                giftPhotos = giftPhotos,
                today = today,
                onBack = onBack,
                onEditPerson = { onEditPerson(state.person) },
                onOpenMore = { moreOpen = true },
                onPlanGift = onPlanGift,
                onAddGift = { onAddGift(state.person) },
                onOpenGift = onOpenGift,
                onAddGiftIdea = { onAddGiftIdea(state.person) },
                onOpenGiftIdea = onOpenGiftIdea,
                onAddOccasion = { onAddOccasion(state.person) },
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    // Each choice closes the sheet first, so coming back to the person never finds it still open.
    if (moreOpen && state is PersonDetailUiState.Loaded) {
        PersonMoreSheet(onDismiss = { moreOpen = false }) {
            PersonMoreContent(
                state = state,
                onAddOccasion = { moreOpen = false; onAddOccasion(state.person) },
                onEditOccasion = { moreOpen = false; onEditOccasion(it) },
                onAddWishlist = { moreOpen = false; onAddWishlist(state.person) },
                onOpenWishlist = { moreOpen = false; onOpenWishlist(it) },
                onDeleteRequested = { moreOpen = false; onDeleteRequested() },
            )
        }
    }

    val person = (state as? PersonDetailUiState.Loaded)?.person
    if (deletion.confirming && person != null) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.people_delete_dialog_title, person.name),
            message = stringResource(R.string.people_delete_dialog_message),
            confirmLabel = stringResource(R.string.people_delete_confirm),
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteDismissed,
        )
    }
}

@Composable
private fun PersonDetailContent(
    state: PersonDetailUiState.Loaded,
    giftPhotos: Map<String, LoadedPhoto>,
    today: LocalDate,
    onBack: (() -> Unit)?,
    onEditPerson: () -> Unit,
    onOpenMore: () -> Unit,
    onPlanGift: (Occasion) -> Unit,
    onAddGift: () -> Unit,
    onOpenGift: (GiftEntry) -> Unit,
    onAddGiftIdea: () -> Unit,
    onOpenGiftIdea: (GiftIdeaEntry) -> Unit,
    onAddOccasion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = remember(state.occasions, today) { nextOccasion(state.occasions, today) }
    val plans = remember(state.giftIdeas) { occasionPlans(state.giftIdeas.map { it.idea }) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .giftiaryReadableWidth(),
    ) {
        item { PersonHero(person = state.person, onBack = onBack, onEdit = onEditPerson, onMore = onOpenMore) }
        next?.let { (occasion, date) ->
            item {
                NextOccasionCard(
                    occasion = occasion,
                    days = ChronoUnit.DAYS.between(today, date).toInt(),
                    plan = plans[occasion.id],
                    onPlanGift = { onPlanGift(occasion) },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        // A new person has no dates yet: offer the first one here. Once there are some, they are
        // managed from "More" with the person's other details.
        if (state.occasions.isEmpty()) {
            item {
                Column(Modifier.padding(top = GiftiarySpacing.l)) {
                    NoOccasions()
                    AddAction(label = stringResource(R.string.person_detail_add_occasion), onClick = onAddOccasion)
                }
            }
        }

        item {
            DetailHeading(
                icon = R.drawable.ic_idea,
                title = stringResource(R.string.person_detail_gift_ideas),
                subtitle = stringResource(R.string.person_detail_gift_ideas_subtitle),
            )
        }
        if (state.giftIdeas.isEmpty()) {
            item { EmptyLine(stringResource(R.string.person_detail_no_gift_ideas)) }
        } else {
            items(state.giftIdeas, key = { it.idea.id }) { entry ->
                GiftIdeaRow(entry = entry, onClick = { onOpenGiftIdea(entry) }, showRecipient = false)
                GiftiaryDivider(modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter))
            }
        }
        item { AddAction(label = stringResource(R.string.person_detail_add_gift_idea), onClick = onAddGiftIdea) }

        item {
            History(
                person = state.person,
                gifts = state.gifts,
                giftPhotos = giftPhotos,
                onOpenGift = onOpenGift,
                onAddGift = onAddGift,
                modifier = Modifier.padding(top = GiftiarySpacing.xl),
            )
        }
    }
}

/** The occasion that comes next, with the day it falls on; none when nothing recurs ahead. */
internal fun nextOccasion(occasions: List<Occasion>, today: LocalDate): Pair<Occasion, LocalDate>? =
    occasions
        .mapNotNull { occasion -> occasion.nextOccurrence(onOrAfter = today)?.let { occasion to it } }
        .minByOrNull { it.second }

/**
 * Person photos are not stored yet, so the hero is always the identity fallback: a quiet aqua
 * surface carrying a large monogram, never an empty photo slot.
 */
@Composable
private fun PersonHero(person: Person, onBack: (() -> Unit)?, onEdit: () -> Unit, onMore: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HERO_HEIGHT)
            .clip(GiftiaryShapes.hero)
            .background(colors.surfaceContext),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 28.dp, y = 18.dp)
                .size(196.dp)
                .background(colors.surfaceContextQuiet, GiftiaryShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            // Sized to its circle, not to the text size: the name is written in the hero, and the
            // monogram only gives it a face.
            val monogramSize = with(LocalDensity.current) { MONOGRAM_SIZE.toSp() }
            Text(
                text = monogramInitial(person.name),
                modifier = Modifier.clearAndSetSemantics {},
                style = GiftiaryType.display.copy(fontSize = monogramSize, lineHeight = monogramSize),
                color = colors.textPrimary,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = GiftiarySpacing.s),
        ) {
            if (onBack != null) {
                HeroCircleButton(R.drawable.ic_arrow_back, stringResource(R.string.navigate_back), onBack)
            }
            Spacer(Modifier.weight(1f))
            HeroCircleButton(R.drawable.ic_edit, stringResource(R.string.person_detail_edit, person.name), onEdit)
            HeroCircleButton(R.drawable.ic_more, stringResource(R.string.person_detail_more, person.name), onMore)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                // Below the hero buttons, so a long name at large text makes the hero taller instead.
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = HERO_BAR_HEIGHT, bottom = GiftiarySpacing.xl),
        ) {
            Text(
                text = person.name,
                modifier = Modifier.semantics { heading() },
                style = GiftiaryType.display,
                color = colors.textPrimary,
            )
            person.relationship?.takeIf { it.isNotBlank() }?.let { relationship ->
                Text(
                    text = relationship,
                    modifier = Modifier.padding(top = GiftiarySpacing.xs),
                    style = GiftiaryType.body.copy(fontWeight = GiftiaryType.contentTitle.fontWeight),
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun NextOccasionCard(
    occasion: Occasion,
    days: Int,
    plan: OccasionPlan?,
    onPlanGift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContext, GiftiaryShapes.band)
            .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = 18.dp, bottom = GiftiarySpacing.xl),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${occasionTitle(occasion)} · ${occasionDayMonth(occasion)}",
                style = GiftiaryType.sectionTitle,
                color = colors.textPrimary,
            )
            PlanLine(plan, modifier = Modifier.padding(top = 6.dp))
            GiftiaryPillButton(
                text = stringResource(R.string.person_detail_plan_gift),
                onClick = onPlanGift,
                modifier = Modifier.padding(top = GiftiarySpacing.l),
                trailingIcon = painterResource(R.drawable.ic_chevron_right),
            )
        }
        DaysCountdown(days = days, large = true)
    }
}

@Composable
private fun DetailHeading(@DrawableRes icon: Int, title: String, subtitle: String? = null) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.xl, bottom = GiftiarySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(16.dp),
            tint = colors.textPrimary,
        )
        Column {
            Text(text = title, modifier = Modifier.semantics { heading() }, style = GiftiaryType.sectionTitle, color = colors.textPrimary)
            subtitle?.let { Text(text = it, style = GiftiaryType.bodySmall, color = colors.textSecondary) }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.s),
        style = GiftiaryType.bodySmall,
        color = GiftiaryTheme.colors.textSecondary,
    )
}

@Composable
private fun NoOccasions() {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.s),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
    ) {
        Text(text = stringResource(R.string.person_detail_no_occasions_title), style = GiftiaryType.contentTitle, color = colors.textPrimary)
        Text(text = stringResource(R.string.person_detail_no_occasions_message), style = GiftiaryType.bodySmall, color = colors.textSecondary)
    }
}

/** "+ Add idea": the quiet coral add of a section, below its last row. */
@Composable
private fun AddAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.padding(horizontal = GiftiarySpacing.s),
        colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.primaryActionText),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = GiftiarySpacing.s),
    ) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(GiftiarySpacing.s))
        Text(label, style = GiftiaryType.actionSmall.copy(fontWeight = GiftiaryType.body.fontWeight))
    }
}

/**
 * "History with Peter" on parchment: the gifts grouped by year and occasion, newest first, two
 * memory cards to a row.
 */
@Composable
private fun History(
    person: Person,
    gifts: List<GiftEntry>,
    giftPhotos: Map<String, LoadedPhoto>,
    onOpenGift: (GiftEntry) -> Unit,
    onAddGift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceAlt, GiftiaryShapes.sheet)
            .padding(top = GiftiarySpacing.s, bottom = GiftiarySpacing.xxl),
    ) {
        DetailHeading(
            icon = R.drawable.ic_history,
            title = stringResource(R.string.person_detail_history, person.name),
            subtitle = if (gifts.isEmpty()) null else pluralStringResource(R.plurals.person_detail_history_count, gifts.size, gifts.size),
        )
        if (gifts.isEmpty()) EmptyLine(stringResource(R.string.person_detail_no_gifts))
        historyGroups(gifts).forEach { group ->
            HistoryGroup(group = group, giftPhotos = giftPhotos, onOpenGift = onOpenGift)
        }
        AddAction(
            label = stringResource(R.string.person_detail_add_gift),
            onClick = onAddGift,
            modifier = Modifier.padding(top = GiftiarySpacing.s),
        )
    }
}

/**
 * The person's secondary actions, kept off the approved page: their occasions and wishlists, and
 * deleting them. The same bottom sheet as Quick Add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonMoreSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = GiftiaryTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = GiftiaryShapes.sheet,
        containerColor = colors.background,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(colors.borderStrong, GiftiaryShapes.pill),
            )
        },
    ) {
        content()
    }
}

@Composable
internal fun PersonMoreContent(
    state: PersonDetailUiState.Loaded,
    onAddOccasion: () -> Unit,
    onEditOccasion: (Occasion) -> Unit,
    onAddWishlist: () -> Unit,
    onOpenWishlist: (PersonWishlist) -> Unit,
    onDeleteRequested: () -> Unit,
) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 26.dp),
    ) {
        Text(
            text = state.person.name,
            modifier = Modifier
                .padding(horizontal = GiftiarySpacing.browseGutter)
                .semantics { heading() },
            style = GiftiaryType.sheetTitle,
            color = colors.textPrimary,
        )
        SheetGroupLabel(stringResource(R.string.person_detail_occasions))
        if (state.occasions.isEmpty()) NoOccasions()
        state.occasions.forEach { occasion ->
            OccasionRow(occasion = occasion, onClick = { onEditOccasion(occasion) }, clickLabel = R.string.occasion_edit)
            GiftiaryDivider(modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter))
        }
        AddAction(label = stringResource(R.string.person_detail_add_occasion), onClick = onAddOccasion)

        SheetGroupLabel(stringResource(R.string.person_detail_wishlists))
        if (state.wishlists.isEmpty()) EmptyLine(stringResource(R.string.person_detail_no_wishlists))
        state.wishlists.forEach { entry ->
            ListRow(
                title = entry.wishlist.name,
                detail = if (entry.wishCount == 0) {
                    stringResource(R.string.person_detail_wishlist_no_wishes)
                } else {
                    pluralStringResource(R.plurals.person_detail_wish_count, entry.wishCount, entry.wishCount)
                },
                onClickLabel = stringResource(R.string.wishlist_open),
                onClick = { onOpenWishlist(entry) },
            )
            GiftiaryDivider(modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter))
        }
        AddAction(label = stringResource(R.string.person_detail_add_wishlist), onClick = onAddWishlist)

        GiftiaryDivider(modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.s))
        // Muted, last and confirmed: deleting never competes with the rest.
        TextButton(
            onClick = onDeleteRequested,
            modifier = Modifier.padding(horizontal = GiftiarySpacing.s),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.textMuted),
        ) {
            Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(GiftiarySpacing.s))
            Text(stringResource(R.string.people_delete, state.person.name), style = GiftiaryType.actionSmall)
        }
    }
}

@Composable
private fun SheetGroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier
            .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.l, bottom = GiftiarySpacing.xs)
            .semantics { heading() },
        style = GiftiaryType.overline,
        color = GiftiaryTheme.colors.textLabel,
    )
}

internal data class HistoryGroup(val year: Int, val occasion: Occasion?, val gifts: List<GiftEntry>)

/** Newest first; within a year, the gifts of one occasion stay together. */
internal fun historyGroups(gifts: List<GiftEntry>): List<HistoryGroup> =
    gifts
        .sortedWith(compareByDescending<GiftEntry> { it.gift.givenAt }.thenByDescending { it.gift.createdAt })
        .groupBy { it.gift.givenAt.year to it.occasion?.id }
        .map { (key, entries) -> HistoryGroup(year = key.first, occasion = entries.first().occasion, gifts = entries) }

@Composable
private fun HistoryGroup(group: HistoryGroup, giftPhotos: Map<String, LoadedPhoto>, onOpenGift: (GiftEntry) -> Unit) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier
            .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.xl, bottom = GiftiarySpacing.m)
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = group.year.toString(),
            style = GiftiaryType.number.copy(fontSize = 34.sp, lineHeight = 34.sp),
            color = colors.textPrimary,
        )
        group.occasion?.let {
            Text(
                text = occasionTitle(it).uppercase(),
                modifier = Modifier.padding(bottom = 3.dp),
                style = GiftiaryType.overline,
                color = colors.textLabel,
            )
        }
    }
    group.gifts.chunked(2).forEach { pair ->
        Row(
            modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, bottom = GiftiarySpacing.l),
            horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
        ) {
            pair.forEach { entry ->
                GiftMemoryCard(
                    entry = entry,
                    photo = entry.gift.photoPath?.let(giftPhotos::get),
                    onClick = { onOpenGift(entry) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

private val HERO_HEIGHT = 266.dp
private val MONOGRAM_SIZE = 104.dp

// The hero buttons' 48dp targets and their vertical padding.
private val HERO_BAR_HEIGHT = 64.dp

private val previewPerson = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)

private fun previewOccasion(type: OccasionType, day: Int, month: Int, year: Int? = null, title: String? = null) =
    Occasion(UUID.randomUUID(), previewPerson.id, type, title, day, month, year, true, Instant.EPOCH, Instant.EPOCH)

@Composable
private fun PersonDetailPreview(state: PersonDetailUiState) {
    GiftiaryTheme {
        PersonDetailScreen(
            state = state,
            onBack = {},
            onEditPerson = {},
            onAddOccasion = {},
            onEditOccasion = {},
            onPlanGift = {},
            onAddGift = {},
            onOpenGift = {},
            onAddGiftIdea = {},
            onOpenGiftIdea = {},
            onAddWishlist = {},
            onOpenWishlist = {},
            onDeleteRequested = {},
            onDeleteConfirmed = {},
            onDeleteDismissed = {},
            onDeleteErrorShown = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PersonDetailPopulatedPreview() {
    PersonDetailPreview(
        PersonDetailUiState.Loaded(
            person = previewPerson,
            occasions = listOf(
                previewOccasion(OccasionType.BIRTHDAY, 12, 3, year = 1990),
                previewOccasion(OccasionType.NAME_DAY, 26, 7),
                previewOccasion(OccasionType.CUSTOM, 1, 9, title = "First day at school"),
            ),
            giftIdeas = emptyList(),
            gifts = emptyList(),
            wishlists = listOf(
                PersonWishlist(Wishlist(UUID.randomUUID(), previewPerson.id, "Christmas", Instant.EPOCH, Instant.EPOCH), 3),
            ),
        ),
    )
}

@PreviewLightDark
@Composable
private fun PersonDetailNoOccasionsPreview() {
    PersonDetailPreview(PersonDetailUiState.Loaded(previewPerson, emptyList(), emptyList(), emptyList(), emptyList()))
}
