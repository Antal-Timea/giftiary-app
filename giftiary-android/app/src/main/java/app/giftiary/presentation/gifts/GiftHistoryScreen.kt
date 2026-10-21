package app.giftiary.presentation.gifts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Gift
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.giftideas.GiftIdeaRow
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * [giftDetail] and [ideaDetail] show the selected item beside the list on a wide window; their
 * `onDeleted` closes it there.
 */
@Composable
fun GiftHistoryScreen(
    onBack: (() -> Unit)?,
    onAddGift: () -> Unit,
    onOpenGift: (UUID) -> Unit,
    onAddGiftIdea: () -> Unit,
    onOpenGiftIdea: (UUID) -> Unit,
    giftDetail: @Composable (id: UUID, onDeleted: () -> Unit) -> Unit,
    ideaDetail: @Composable (id: UUID, onDeleted: () -> Unit) -> Unit,
    viewModel: GiftHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdaptiveGiftHistoryScreen(
        state = state,
        onBack = onBack,
        onFilterChange = viewModel::onFilterChange,
        onAddGift = onAddGift,
        onOpenGift = onOpenGift,
        onAddGiftIdea = onAddGiftIdea,
        onOpenGiftIdea = onOpenGiftIdea,
        onSelectGift = viewModel::onGiftSelected,
        onSelectIdea = viewModel::onIdeaSelected,
        onClearSelection = viewModel::onSelectionCleared,
        giftDetail = giftDetail,
        ideaDetail = ideaDetail,
    )
}

@Composable
fun GiftHistoryScreen(
    state: GiftHistoryUiState,
    onBack: (() -> Unit)?,
    onFilterChange: (GiftFilter) -> Unit,
    onAddGift: () -> Unit,
    onOpenGift: (GiftEntry) -> Unit,
    onAddGiftIdea: () -> Unit,
    onOpenGiftIdea: (GiftIdeaEntry) -> Unit,
    modifier: Modifier = Modifier,
    // True beside a detail pane: rows then show which gift or idea is open there.
    selectable: Boolean = false,
) {
    val colors = GiftiaryTheme.colors
    val selection = state.selection.takeIf { selectable }
    val showsIdeas = state.filter == GiftFilter.IDEAS
    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = stringResource(R.string.gifts_title), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = if (showsIdeas) onAddGiftIdea else onAddGift,
                containerColor = colors.primaryAction,
                contentColor = colors.onPrimaryAction,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(GiftiarySpacing.m))
                Text(stringResource(if (showsIdeas) R.string.gift_ideas_add else R.string.gifts_add))
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .giftiaryReadableWidth(),
        ) {
            // Always shown: even without any gifts, the ideas filter must stay reachable.
            FilterRow(selected = state.filter, onSelect = onFilterChange)
            when (val content = state.content) {
                GiftHistoryContent.Loading -> LoadingContent()
                GiftHistoryContent.LoadFailed -> MessageContent(stringResource(R.string.gifts_load_failed))
                GiftHistoryContent.NoGifts -> MessageContent(
                    title = stringResource(R.string.gifts_empty_title),
                    message = stringResource(R.string.gifts_empty_message),
                )
                GiftHistoryContent.NoMatches -> MessageContent(
                    message = stringResource(
                        when (state.filter) {
                            GiftFilter.GIVEN -> R.string.gifts_no_given
                            GiftFilter.RECEIVED -> R.string.gifts_no_received
                            GiftFilter.ALL, GiftFilter.IDEAS -> R.string.gifts_empty_title
                        },
                    ),
                )
                GiftHistoryContent.NoIdeas -> MessageContent(
                    title = stringResource(R.string.gift_ideas_empty_title),
                    message = stringResource(R.string.gift_ideas_empty_message),
                )
                is GiftHistoryContent.IdeasLoaded -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
                ) {
                    items(content.ideas, key = { it.idea.id }) { entry ->
                        GiftIdeaRow(
                            entry = entry,
                            onClick = { onOpenGiftIdea(entry) },
                            selectable = selectable,
                            selected = selection == GiftHistorySelection.Idea(entry.idea.id),
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
                    }
                }
                is GiftHistoryContent.Loaded -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
                ) {
                    items(content.gifts, key = { it.gift.id }) { entry ->
                        GiftRow(
                            entry = entry,
                            onClick = { onOpenGift(entry) },
                            selectable = selectable,
                            selected = selection == GiftHistorySelection.Gift(entry.gift.id),
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(selected: GiftFilter, onSelect: (GiftFilter) -> Unit) {
    val colors = GiftiaryTheme.colors
    // Scrollable so further filters fit on narrow screens and at large font sizes.
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
    ) {
        GiftFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filterLabel(filter)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.background,
                    labelColor = colors.textSecondary,
                    selectedContainerColor = colors.surfaceContext,
                    selectedLabelColor = colors.textPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter == selected,
                    borderColor = colors.border,
                    selectedBorderColor = colors.surfaceContext,
                ),
            )
        }
    }
}

@Composable
private fun filterLabel(filter: GiftFilter): String = when (filter) {
    GiftFilter.ALL -> stringResource(R.string.gifts_filter_all)
    GiftFilter.GIVEN -> giftDirectionLabel(GiftDirection.GIVEN)
    GiftFilter.RECEIVED -> giftDirectionLabel(GiftDirection.RECEIVED)
    GiftFilter.IDEAS -> stringResource(R.string.gifts_filter_ideas)
}

// Keeps the last row reachable above the floating action button.
private val FAB_CLEARANCE = 96.dp

private val previewAnna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)

private fun previewGift(name: String, direction: GiftDirection, date: String) = GiftEntry(
    gift = Gift(
        id = UUID.randomUUID(),
        direction = direction,
        giverPersonId = if (direction == GiftDirection.RECEIVED) previewAnna.id else null,
        recipientPersonId = if (direction == GiftDirection.GIVEN) previewAnna.id else null,
        occasionId = null,
        sourceGiftIdeaId = null,
        name = name,
        givenAt = LocalDate.parse(date),
        price = null,
        photoPath = null,
        productUrl = null,
        note = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    ),
    giver = previewAnna.takeIf { direction == GiftDirection.RECEIVED },
    recipient = previewAnna.takeIf { direction == GiftDirection.GIVEN },
    occasion = null,
)

@PreviewLightDark
@Composable
private fun GiftHistoryPreview() {
    GiftiaryTheme {
        GiftHistoryScreen(
            state = GiftHistoryUiState(
                filter = GiftFilter.ALL,
                content = GiftHistoryContent.Loaded(
                    listOf(
                        previewGift("Birthday book", GiftDirection.GIVEN, "2026-03-12"),
                        previewGift("Headphones", GiftDirection.RECEIVED, "2025-11-04"),
                    ),
                ),
            ),
            onBack = {},
            onFilterChange = {},
            onAddGift = {},
            onOpenGift = {},
            onAddGiftIdea = {},
            onOpenGiftIdea = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun GiftHistoryEmptyPreview() {
    GiftiaryTheme {
        GiftHistoryScreen(
            state = GiftHistoryUiState(GiftFilter.ALL, GiftHistoryContent.NoGifts),
            onBack = {},
            onFilterChange = {},
            onAddGift = {},
            onOpenGift = {},
            onAddGiftIdea = {},
            onOpenGiftIdea = {},
        )
    }
}
