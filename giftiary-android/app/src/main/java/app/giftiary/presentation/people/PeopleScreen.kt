package app.giftiary.presentation.people

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Person
import app.giftiary.presentation.components.GiftCutout
import app.giftiary.presentation.components.GiftiaryAddFab
import app.giftiary.presentation.components.GiftiaryAddFabClearance
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryPillButton
import app.giftiary.presentation.components.GiftiaryRow
import app.giftiary.presentation.components.GiftiarySearchField
import app.giftiary.presentation.components.GiftiaryTextAction
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.RowChevron
import app.giftiary.presentation.components.RowSupportingText
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.home.ComingUpBand
import app.giftiary.presentation.home.ComingUpContent
import app.giftiary.presentation.home.ComingUpViewModel
import app.giftiary.presentation.navigation.hasNavigationRail
import app.giftiary.presentation.occasions.occasionShortDayMonth
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.util.UUID

/** [personDetail] is the selected person's detail, shown beside the list on a wide window. */
@Composable
fun PeopleScreen(
    onAddPerson: () -> Unit,
    onOpenPerson: (UUID) -> Unit,
    personDetail: @Composable (UUID) -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
    comingUpViewModel: ComingUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val comingUp by comingUpViewModel.content.collectAsStateWithLifecycle()
    AdaptivePeopleScreen(
        state = state,
        comingUp = comingUp,
        onQueryChange = viewModel::onQueryChange,
        onAddPerson = onAddPerson,
        onOpenPerson = onOpenPerson,
        onSelectPerson = viewModel::onPersonSelected,
        onClearSelection = viewModel::onSelectionCleared,
        personDetail = personDetail,
    )
}

/**
 * The People root: title, search, the people whose occasions are coming up, then everyone.
 * Adding is the navigation bar's global action on phones; beside the rail there is no such action,
 * so the list keeps its own add button there.
 */
@Composable
fun PeopleScreen(
    state: PeopleUiState,
    comingUp: ComingUpContent,
    onQueryChange: (String) -> Unit,
    onAddPerson: () -> Unit,
    onOpenPerson: (Person) -> Unit,
    modifier: Modifier = Modifier,
    // True beside a detail pane: rows then show which person is open there.
    selectable: Boolean = false,
    showAddButton: Boolean = LocalWidthClass.current.hasNavigationRail,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (showAddButton) GiftiaryAddFab(text = stringResource(R.string.people_add), onClick = onAddPerson)
        },
    ) { contentPadding ->
        val contentModifier = Modifier
            .padding(contentPadding)
            .windowInsetsPadding(WindowInsets.statusBars)
        when (val content = state.content) {
            PeopleContent.Loading -> LoadingContent(contentModifier)
            PeopleContent.LoadFailed -> MessageContent(stringResource(R.string.people_load_failed), contentModifier)
            is PeopleContent.Loaded -> if (content.people.isEmpty()) {
                Column(contentModifier.fillMaxSize().giftiaryReadableWidth()) {
                    PeopleHeader()
                    EmptyPeople(onAddPerson = onAddPerson)
                }
            } else {
                PeopleList(
                    state = state,
                    allCount = content.people.size,
                    comingUp = comingUp,
                    selectable = selectable,
                    onQueryChange = onQueryChange,
                    onOpenPerson = onOpenPerson,
                    bottomClearance = if (showAddButton) GiftiaryAddFabClearance else GiftiarySpacing.xl,
                    modifier = contentModifier,
                )
            }
        }
    }
}

@Composable
private fun PeopleList(
    state: PeopleUiState,
    allCount: Int,
    comingUp: ComingUpContent,
    selectable: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenPerson: (Person) -> Unit,
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
) {
    val people = state.matchingPeople
    val searching = state.query.isNotBlank()
    val selectedPersonId = state.selectedPersonId.takeIf { selectable }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .giftiaryReadableWidth(),
        contentPadding = PaddingValues(bottom = bottomClearance),
    ) {
        item { PeopleHeader() }
        item {
            GiftiarySearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.people_search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.l),
            )
        }
        if (searching && people.isEmpty()) {
            item { NoResults(query = state.query.trim(), allCount = allCount, onClear = { onQueryChange("") }) }
            return@LazyColumn
        }
        // A search is about finding someone; what is coming up waits until it is cleared.
        if (!searching) {
            item {
                ComingUpBand(
                    content = comingUp.forPeople(),
                    onOpen = { item -> item.upcoming.person?.let(onOpenPerson) },
                    modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter),
                    showRelationship = true,
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.people_all),
                modifier = Modifier
                    .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = 26.dp, bottom = GiftiarySpacing.xs)
                    .semantics { heading() },
                style = GiftiaryType.editorialSection,
                color = GiftiaryTheme.colors.textPrimary,
            )
        }
        items(people, key = { it.id }) { person ->
            PersonRow(
                person = person,
                birthday = state.birthdays[person.id]?.let { "${occasionTitle(it)} ${occasionShortDayMonth(it.day, it.month)}" },
                selected = person.id == selectedPersonId,
                selectable = selectable,
                onClick = { onOpenPerson(person) },
            )
            GiftiaryDivider(
                modifier = Modifier.padding(end = GiftiarySpacing.browseGutter),
                startIndent = GiftiarySpacing.browseGutter + AVATAR_SIZE + 14.dp,
            )
        }
    }
}

// People opens a person, so occasions without one (Christmas, the user's own) are Home's.
private fun ComingUpContent.forPeople(): ComingUpContent =
    if (this is ComingUpContent.Loaded) ComingUpContent.Loaded(items.filter { it.upcoming.person != null }) else this

@Composable
private fun PeopleHeader() {
    val colors = GiftiaryTheme.colors
    Column(modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.xl)) {
        Text(
            text = stringResource(R.string.people_title),
            modifier = Modifier.semantics { heading() },
            style = GiftiaryType.screenTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.people_subtitle),
            modifier = Modifier.padding(top = GiftiarySpacing.xs),
            style = GiftiaryType.body,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun PersonRow(
    person: Person,
    birthday: String?,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
) {
    val detail = listOfNotNull(person.relationship?.takeIf { it.isNotBlank() }, birthday).joinToString(" · ")
    GiftiaryRow(
        title = person.name,
        modifier = Modifier
            .background(if (selected) GiftiaryTheme.colors.surfaceContext else Color.Transparent)
            // Phones have no selection, so their rows are not announced as "not selected".
            .then(if (selectable) Modifier.semantics { this.selected = selected } else Modifier),
        supporting = detail.takeIf { it.isNotEmpty() }?.let { { RowSupportingText(it) } },
        // The selected row is already aqua; the avatar takes the in-band fill so it stays visible.
        leading = { GiftiaryAvatar(name = person.name, size = AVATAR_SIZE, onContext = selected) },
        trailing = { RowChevron() },
        onClickLabel = stringResource(R.string.people_open),
        onClick = onClick,
    )
}

/** No people at all: an invitation to add the first, with the in-place action the board asks for. */
@Composable
private fun EmptyPeople(onAddPerson: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        // The approved small gift (G1-4), close up in the circle; decoration, as the title says it all.
        Image(
            painter = painterResource(GiftCutout.SMALL.image),
            contentDescription = null,
            modifier = Modifier
                .size(128.dp)
                .clip(GiftiaryShapes.pill)
                .background(colors.thumbnailFallback)
                .scale(EMPTY_MARK_ZOOM),
        )
        Text(
            text = stringResource(R.string.people_empty_title),
            modifier = Modifier
                .padding(top = GiftiarySpacing.xl)
                .semantics { heading() },
            style = GiftiaryType.sheetTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.people_empty_message),
            modifier = Modifier.padding(top = GiftiarySpacing.s),
            style = GiftiaryType.body,
            color = colors.textSecondary,
        )
        GiftiaryPillButton(
            text = stringResource(R.string.people_add),
            onClick = onAddPerson,
            modifier = Modifier.padding(top = GiftiarySpacing.xl),
            leadingIcon = painterResource(R.drawable.ic_add),
        )
    }
}

/** The search found no one: say so for this search only, and undo it in place. */
@Composable
private fun NoResults(query: String, allCount: Int, onClear: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.l),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(17.dp),
            tint = colors.textSecondary,
        )
        Column {
            Text(
                text = stringResource(R.string.people_no_results_title, query),
                modifier = Modifier.semantics { heading() },
                style = GiftiaryType.sectionTitle.copy(fontFamily = GiftiaryType.body.fontFamily),
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.people_no_results_message),
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
            Text(
                text = pluralStringResource(R.plurals.people_no_results_count, allCount, allCount),
                modifier = Modifier.padding(top = GiftiarySpacing.l),
                style = GiftiaryType.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

private val AVATAR_SIZE = 48.dp

// The circle crops tightly into the gift, as the approved empty-state mark does.
private const val EMPTY_MARK_ZOOM = 1.3f

// Also used by the list-detail previews in PeopleListDetail.kt.
internal val previewPeople = listOf(
    Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH),
    Person(UUID.randomUUID(), "Péter", null, null, null, Instant.EPOCH, Instant.EPOCH),
    Person(UUID.randomUUID(), "Grandma Ilona", "Grandmother", null, null, Instant.EPOCH, Instant.EPOCH),
)

@Composable
private fun PeopleScreenPreview(state: PeopleUiState) {
    GiftiaryTheme {
        PeopleScreen(
            state = state,
            comingUp = ComingUpContent.Loaded(emptyList()),
            onQueryChange = {},
            onAddPerson = {},
            onOpenPerson = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PeoplePopulatedPreview() {
    PeopleScreenPreview(PeopleUiState(content = PeopleContent.Loaded(previewPeople)))
}

@PreviewLightDark
@Composable
private fun PeopleEmptyPreview() {
    PeopleScreenPreview(PeopleUiState(content = PeopleContent.Loaded(emptyList())))
}

@PreviewLightDark
@Composable
private fun PeopleNoResultsPreview() {
    PeopleScreenPreview(PeopleUiState(content = PeopleContent.Loaded(previewPeople), query = "Robert"))
}
