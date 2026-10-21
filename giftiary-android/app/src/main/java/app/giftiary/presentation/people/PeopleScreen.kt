package app.giftiary.presentation.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Person
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.Lora
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.util.UUID

/** [personDetail] is the selected person's detail, shown beside the list on a wide window. */
@Composable
fun PeopleScreen(
    onBack: (() -> Unit)?,
    onAddPerson: () -> Unit,
    onOpenPerson: (UUID) -> Unit,
    personDetail: @Composable (UUID) -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdaptivePeopleScreen(
        state = state,
        onBack = onBack,
        onAddPerson = onAddPerson,
        onOpenPerson = onOpenPerson,
        onSelectPerson = viewModel::onPersonSelected,
        onClearSelection = viewModel::onSelectionCleared,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onDeleteErrorShown = viewModel::onDeleteErrorShown,
        personDetail = personDetail,
    )
}

@Composable
fun PeopleScreen(
    state: PeopleUiState,
    onBack: (() -> Unit)?,
    onAddPerson: () -> Unit,
    onOpenPerson: (Person) -> Unit,
    onDeleteRequested: (Person) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    // True beside a detail pane: rows then show which person is open there.
    selectable: Boolean = false,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteFailedMessage = stringResource(R.string.people_delete_failed)
    LaunchedEffect(state.deleteFailed) {
        if (state.deleteFailed) {
            snackbarHostState.showSnackbar(deleteFailedMessage)
            onDeleteErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = stringResource(R.string.people_title), onBack = onBack) },
        floatingActionButton = { AddPersonButton(onClick = onAddPerson) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            PeopleContent.Loading -> LoadingContent(contentModifier)
            PeopleContent.LoadFailed -> MessageContent(stringResource(R.string.people_load_failed), contentModifier)
            is PeopleContent.Loaded -> if (content.people.isEmpty()) {
                MessageContent(
                    title = stringResource(R.string.people_empty_title),
                    message = stringResource(R.string.people_empty_message),
                    modifier = contentModifier,
                )
            } else {
                PeopleList(
                    people = content.people,
                    selectedPersonId = state.selectedPersonId.takeIf { selectable },
                    selectable = selectable,
                    onOpenPerson = onOpenPerson,
                    onDeleteRequested = onDeleteRequested,
                    modifier = contentModifier,
                )
            }
        }
    }

    state.personPendingDeletion?.let { person ->
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
private fun AddPersonButton(onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    // The content overload keeps the label in the semantics tree; the animated text/icon
    // overload left the button unlabeled for accessibility services.
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = colors.primaryAction,
        contentColor = colors.onPrimaryAction,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    ) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
        Spacer(Modifier.width(GiftiarySpacing.m))
        Text(stringResource(R.string.people_add))
    }
}

@Composable
private fun PeopleList(
    people: List<Person>,
    selectedPersonId: UUID?,
    selectable: Boolean,
    onOpenPerson: (Person) -> Unit,
    onDeleteRequested: (Person) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .giftiaryReadableWidth(),
        contentPadding = PaddingValues(top = GiftiarySpacing.s, bottom = FAB_CLEARANCE),
    ) {
        items(people, key = { it.id }) { person ->
            PersonRow(
                person = person,
                selected = person.id == selectedPersonId,
                selectable = selectable,
                onClick = { onOpenPerson(person) },
                onDeleteClick = { onDeleteRequested(person) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = GiftiarySpacing.l + MONOGRAM_SIZE + GiftiarySpacing.l),
                color = GiftiaryTheme.colors.divider,
            )
        }
    }
}

@Composable
private fun PersonRow(
    person: Person,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.surfaceContext else Color.Transparent)
            // Phones have no selection, so their rows are not announced as "not selected".
            .then(if (selectable) Modifier.semantics { this.selected = selected } else Modifier)
            .clickable(onClickLabel = stringResource(R.string.people_open), onClick = onClick)
            .padding(start = GiftiarySpacing.l, end = GiftiarySpacing.xs, top = GiftiarySpacing.m, bottom = GiftiarySpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(name = person.name, onSelectedRow = selected)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = GiftiarySpacing.l),
        ) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            person.relationship?.let { relationship ->
                Text(
                    text = relationship,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        // Muted so deleting never competes with opening the person.
        IconButton(onClick = onDeleteClick) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.people_delete, person.name),
                tint = colors.textMuted,
            )
        }
    }
}

/** Decorative initial; the name next to it already carries the meaning. */
@Composable
private fun Monogram(name: String, onSelectedRow: Boolean) {
    val initial = name.substring(0, name.offsetByCodePoints(0, 1)).uppercase()
    val colors = GiftiaryTheme.colors
    Box(
        modifier = Modifier
            .size(MONOGRAM_SIZE)
            // The selected row is already surfaceContext; the quieter tone keeps the circle visible.
            .background(if (onSelectedRow) colors.surfaceContextQuiet else colors.surfaceContext, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Lora),
            color = GiftiaryTheme.colors.textPrimary,
        )
    }
}

private val MONOGRAM_SIZE = 40.dp

// Keeps the last row reachable above the floating action button.
private val FAB_CLEARANCE = 96.dp

private val previewPeople = listOf(
    Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH),
    Person(UUID.randomUUID(), "Péter", null, null, null, Instant.EPOCH, Instant.EPOCH),
    Person(UUID.randomUUID(), "Grandma Ilona", "Grandmother", null, null, Instant.EPOCH, Instant.EPOCH),
)

@Composable
private fun PeopleScreenPreview(state: PeopleUiState) {
    GiftiaryTheme {
        PeopleScreen(
            state = state,
            onBack = {},
            onAddPerson = {},
            onOpenPerson = {},
            onDeleteRequested = {},
            onDeleteConfirmed = {},
            onDeleteDismissed = {},
            onDeleteErrorShown = {},
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
private fun PeopleDeleteDialogPreview() {
    PeopleScreenPreview(
        PeopleUiState(
            content = PeopleContent.Loaded(previewPeople),
            personPendingDeletion = previewPeople.first(),
        ),
    )
}
