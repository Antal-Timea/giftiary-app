package app.giftiary.presentation.gifts

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Gift
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.presentation.components.GiftThumbnail
import app.giftiary.presentation.components.GiftiaryAddFab
import app.giftiary.presentation.components.GiftiaryAddFabClearance
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryFilterChip
import app.giftiary.presentation.components.GiftiaryPillButton
import app.giftiary.presentation.components.GiftiaryRow
import app.giftiary.presentation.components.GiftiarySearchField
import app.giftiary.presentation.components.GiftiaryTextAction
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.arrow
import app.giftiary.presentation.components.directionColor
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.navigation.hasNavigationRail
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.UUID

/** [giftDetail] is the selected gift's detail, shown beside the list on a wide window. */
@Composable
fun GiftHistoryScreen(
    onAddGift: () -> Unit,
    onOpenGift: (UUID) -> Unit,
    giftDetail: @Composable (id: UUID, onDeleted: () -> Unit) -> Unit,
    viewModel: GiftHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdaptiveGiftHistoryScreen(
        state = state,
        photos = viewModel.photos,
        onFilterChange = viewModel::onFilterChange,
        onQueryChange = viewModel::onQueryChange,
        onAddGift = onAddGift,
        onOpenGift = onOpenGift,
        onSelectGift = viewModel::onGiftSelected,
        onClearSelection = viewModel::onSelectionCleared,
        giftDetail = giftDetail,
    )
}

/**
 * The Gifts root: title, search, the direction filters, then every gift by year and month.
 * Adding is the navigation bar's global action on phones; beside the rail there is no such action,
 * so the history keeps its own add button there.
 */
@Composable
fun GiftHistoryScreen(
    state: GiftHistoryUiState,
    photos: ShownPhotos,
    onFilterChange: (GiftFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onAddGift: () -> Unit,
    onOpenGift: (GiftEntry) -> Unit,
    modifier: Modifier = Modifier,
    // True beside a detail pane: rows then show which gift is open there.
    selectable: Boolean = false,
    showAddButton: Boolean = LocalWidthClass.current.hasNavigationRail,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (showAddButton) GiftiaryAddFab(text = stringResource(R.string.gifts_add), onClick = onAddGift)
        },
    ) { contentPadding ->
        val contentModifier = Modifier
            .padding(contentPadding)
            .windowInsetsPadding(WindowInsets.statusBars)
        when (val content = state.content) {
            GiftHistoryContent.Loading -> LoadingContent(contentModifier)
            // Nothing could be read, so there is no saved history this message would hide.
            GiftHistoryContent.LoadFailed -> MessageContent(stringResource(R.string.gifts_load_failed), contentModifier)
            GiftHistoryContent.NoGifts -> Column(contentModifier.fillMaxSize().giftiaryReadableWidth()) {
                HistoryHeader()
                EmptyHistory(onAddGift = onAddGift)
            }
            else -> HistoryList(
                state = state,
                photos = photos,
                selectable = selectable,
                onFilterChange = onFilterChange,
                onQueryChange = onQueryChange,
                onOpenGift = onOpenGift,
                bottomClearance = if (showAddButton) GiftiaryAddFabClearance else GiftiarySpacing.xl,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun HistoryList(
    state: GiftHistoryUiState,
    photos: ShownPhotos,
    selectable: Boolean,
    onFilterChange: (GiftFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenGift: (GiftEntry) -> Unit,
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
) {
    val selectedGiftId = state.selectedGiftId.takeIf { selectable }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .giftiaryReadableWidth(),
        contentPadding = PaddingValues(bottom = bottomClearance),
    ) {
        item { HistoryHeader() }
        item {
            GiftiarySearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.gifts_search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.l),
            )
        }
        item { Filters(selected = state.filter, onFilterChange = onFilterChange) }
        when (val content = state.content) {
            is GiftHistoryContent.NoMatches -> item {
                NoMatches(query = state.query.trim(), filter = state.filter, total = content.total, onClear = { onQueryChange("") })
            }
            is GiftHistoryContent.Loaded -> history(
                content = content,
                photos = photos,
                selectedGiftId = selectedGiftId,
                selectable = selectable,
                onOpenGift = onOpenGift,
            )
            else -> Unit
        }
    }
}

private fun LazyListScope.history(
    content: GiftHistoryContent.Loaded,
    photos: ShownPhotos,
    selectedGiftId: UUID?,
    selectable: Boolean,
    onOpenGift: (GiftEntry) -> Unit,
) {
    content.years.forEachIndexed { yearIndex, year ->
        item(key = "year-${year.year}") { YearHeading(year.year, first = yearIndex == 0) }
        year.months.forEach { month ->
            item(key = "month-${month.month}") { MonthHeading(month) }
            month.gifts.forEachIndexed { index, entry ->
                item(key = entry.gift.id) {
                    if (index > 0) {
                        GiftiaryDivider(
                            modifier = Modifier.padding(end = GiftiarySpacing.browseGutter),
                            startIndent = GiftiarySpacing.browseGutter + THUMBNAIL_SIZE + 14.dp,
                        )
                    }
                    HistoryRow(
                        entry = entry,
                        photo = photos.photoOf(entry.gift.photoPath),
                        selected = entry.gift.id == selectedGiftId,
                        selectable = selectable,
                        onClick = { onOpenGift(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader() {
    val colors = GiftiaryTheme.colors
    Column(modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.section)) {
        Text(
            text = stringResource(R.string.gifts_history_title),
            modifier = Modifier.semantics { heading() },
            style = GiftiaryType.screenTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.gifts_subtitle),
            modifier = Modifier.padding(top = 6.dp),
            style = GiftiaryType.bodySmall,
            color = colors.textSecondary,
        )
    }
}

/** All, then the two directions, each with its arrow so the choice never rests on colour alone. */
@Composable
private fun Filters(selected: GiftFilter, onFilterChange: (GiftFilter) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.s),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
    ) {
        GiftFilter.entries.forEach { filter ->
            GiftiaryFilterChip(
                text = filterLabel(filter),
                selected = filter == selected,
                onClick = { onFilterChange(filter) },
                direction = when (filter) {
                    GiftFilter.ALL -> null
                    GiftFilter.GIVEN -> GiftDirection.GIVEN
                    GiftFilter.RECEIVED -> GiftDirection.RECEIVED
                },
            )
        }
    }
}

@Composable
private fun YearHeading(year: Int, first: Boolean) {
    Text(
        text = year.toString(),
        modifier = Modifier
            .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = if (first) GiftiarySpacing.l else 30.dp)
            .semantics { heading() },
        style = GiftiaryType.screenTitle,
        color = GiftiaryTheme.colors.textPrimary,
    )
}

@Composable
private fun MonthHeading(month: GiftMonth) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = month.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale),
        modifier = Modifier
            .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.l, bottom = GiftiarySpacing.xs)
            .semantics { heading() },
        style = GiftiaryType.overline,
        color = GiftiaryTheme.colors.textLabel,
    )
}

/**
 * One gift: its photo or the branded glyph, its name, and a direction line in the direction's
 * colour with its arrow and words ("Received from Peter · Birthday"); the price when known.
 */
@Composable
private fun HistoryRow(
    entry: GiftEntry,
    photo: LoadedPhoto?,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
) {
    val colors = GiftiaryTheme.colors
    val direction = entry.gift.direction
    GiftiaryRow(
        title = entry.gift.name,
        modifier = Modifier
            .background(if (selected) colors.surfaceContext else Color.Transparent)
            // Phones have no selection, so their rows are not announced as "not selected".
            .then(if (selectable) Modifier.semantics { this.selected = selected } else Modifier),
        titleStyle = GiftiaryType.contentTitle,
        contentPadding = PaddingValues(horizontal = GiftiarySpacing.browseGutter, vertical = 10.dp),
        leading = {
            val image = rememberDecodedImage(photo, THUMBNAIL_SIZE)
            GiftThumbnail(
                size = THUMBNAIL_SIZE,
                photo = image?.let {
                    // The gift's name is written beside it; the photo adds no information of its own.
                    { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                },
            )
        },
        supporting = {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(direction.arrow),
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = directionColor(direction),
                )
                Text(
                    text = listOfNotNull(giftDirectionLine(entry), entry.occasion?.let { occasionTitle(it) }).joinToString(" · "),
                    style = GiftiaryType.metadata.copy(fontWeight = GiftiaryType.contentTitle.fontWeight),
                    color = directionColor(direction),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entry.gift.price?.let { price ->
                Text(
                    text = giftPrice(price),
                    style = GiftiaryType.metadata,
                    color = colors.textTertiary,
                )
            }
        },
        onClickLabel = stringResource(R.string.gift_open),
        onClick = onClick,
    )
}

/** No gifts at all: an invitation to add the first, as People's empty state does. */
@Composable
private fun EmptyHistory(onAddGift: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .background(colors.thumbnailFallback, GiftiaryShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_gift_glyph),
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = colors.textLabel,
            )
        }
        Text(
            text = stringResource(R.string.gifts_empty_title),
            modifier = Modifier
                .padding(top = GiftiarySpacing.xl)
                .semantics { heading() },
            style = GiftiaryType.sheetTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.gifts_empty_message),
            modifier = Modifier.padding(top = GiftiarySpacing.s),
            style = GiftiaryType.body,
            color = colors.textSecondary,
        )
        GiftiaryPillButton(
            text = stringResource(R.string.gifts_add),
            onClick = onAddGift,
            modifier = Modifier.padding(top = GiftiarySpacing.xl),
            leadingIcon = painterResource(R.drawable.ic_add),
        )
    }
}

/**
 * Nothing matches. A search says so for that search and undoes it in place; a filter alone says
 * there are no gifts that way yet. Either way the rest of the history is still there.
 */
@Composable
private fun NoMatches(query: String, filter: GiftFilter, total: Int, onClear: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
    ) {
        Icon(
            painter = painterResource(if (query.isEmpty()) R.drawable.ic_gift_glyph else R.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(17.dp),
            tint = colors.textSecondary,
        )
        Column {
            Text(
                text = when {
                    query.isNotEmpty() -> stringResource(R.string.gifts_no_results_title, query)
                    filter == GiftFilter.GIVEN -> stringResource(R.string.gifts_no_given)
                    else -> stringResource(R.string.gifts_no_received)
                },
                modifier = Modifier.semantics { heading() },
                style = GiftiaryType.sectionTitle.copy(fontFamily = GiftiaryType.body.fontFamily),
                color = colors.textPrimary,
            )
            if (query.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.gifts_no_results_message),
                    modifier = Modifier.padding(top = GiftiarySpacing.xs),
                    style = GiftiaryType.body,
                    color = colors.textSecondary,
                )
                GiftiaryTextAction(
                    text = stringResource(R.string.search_clear),
                    onClick = onClear,
                    modifier = Modifier.padding(top = GiftiarySpacing.s),
                    underline = true,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.gifts_no_results_count, total, total),
                modifier = Modifier.padding(top = GiftiarySpacing.l),
                style = GiftiaryType.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun filterLabel(filter: GiftFilter): String = when (filter) {
    GiftFilter.ALL -> stringResource(R.string.gifts_filter_all)
    GiftFilter.GIVEN -> giftDirectionLabel(GiftDirection.GIVEN)
    GiftFilter.RECEIVED -> giftDirectionLabel(GiftDirection.RECEIVED)
}

private val THUMBNAIL_SIZE = 52.dp

// Also used by the list-detail previews in GiftHistoryListDetail.kt.
internal val previewGifts: List<GiftEntry> = run {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    val peter = Person(UUID.randomUUID(), "Péter", null, null, null, Instant.EPOCH, Instant.EPOCH)
    fun gift(name: String, direction: GiftDirection, date: LocalDate, person: Person, price: Money? = null) = GiftEntry(
        gift = Gift(
            id = UUID.randomUUID(),
            direction = direction,
            giverPersonId = person.id.takeIf { direction == GiftDirection.RECEIVED },
            recipientPersonId = person.id.takeIf { direction == GiftDirection.GIVEN },
            occasionId = null,
            sourceGiftIdeaId = null,
            name = name,
            givenAt = date,
            price = price,
            photoPath = null,
            productUrl = null,
            note = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
        giver = person.takeIf { direction == GiftDirection.RECEIVED },
        recipient = person.takeIf { direction == GiftDirection.GIVEN },
        occasion = null,
    )
    listOf(
        gift("Scarf", GiftDirection.GIVEN, LocalDate.of(2026, 9, 12), anna, Money(12990, "HUF")),
        gift("Watch", GiftDirection.RECEIVED, LocalDate.of(2026, 6, 2), peter),
        gift("Cookbook", GiftDirection.GIVEN, LocalDate.of(2025, 12, 24), peter),
    )
}

@PreviewLightDark
@Composable
private fun GiftHistoryPreview() {
    GiftiaryTheme {
        GiftHistoryScreen(
            state = GiftHistoryUiState(content = GiftHistoryContent.Loaded(groupByYearAndMonth(previewGifts))),
            photos = ShownPhotos.of(emptyMap()),
            onFilterChange = {},
            onQueryChange = {},
            onAddGift = {},
            onOpenGift = {},
        )
    }
}
