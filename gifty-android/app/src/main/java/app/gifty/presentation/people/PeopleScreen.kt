package app.gifty.presentation.people

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.model.Person
import app.gifty.presentation.components.GiftyConfirmDialog
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.Lora
import app.gifty.presentation.theme.GiftyTheme
import java.time.Instant
import java.util.UUID

@Composable
fun PeopleScreen(
    onBack: () -> Unit,
    onAddPerson: () -> Unit,
    onOpenPerson: (UUID) -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PeopleScreen(
        state = state,
        onBack = onBack,
        onAddPerson = onAddPerson,
        onOpenPerson = { onOpenPerson(it.id) },
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onDeleteErrorShown = viewModel::onDeleteErrorShown,
    )
}

@Composable
fun PeopleScreen(
    state: PeopleUiState,
    onBack: () -> Unit,
    onAddPerson: () -> Unit,
    onOpenPerson: (Person) -> Unit,
    onDeleteRequested: (Person) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
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
        topBar = { GiftyTopBar(title = stringResource(R.string.people_title), onBack = onBack) },
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
                    onOpenPerson = onOpenPerson,
                    onDeleteRequested = onDeleteRequested,
                    modifier = contentModifier,
                )
            }
        }
    }

    state.personPendingDeletion?.let { person ->
        GiftyConfirmDialog(
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
    val colors = GiftyTheme.colors
    // The content overload keeps the label in the semantics tree; the animated text/icon
    // overload left the button unlabeled for accessibility services.
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = colors.primaryAction,
        contentColor = colors.onPrimaryAction,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    ) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
        Spacer(Modifier.width(GiftySpacing.m))
        Text(stringResource(R.string.people_add))
    }
}

@Composable
private fun PeopleList(
    people: List<Person>,
    onOpenPerson: (Person) -> Unit,
    onDeleteRequested: (Person) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = GiftySpacing.s, bottom = FAB_CLEARANCE),
    ) {
        items(people, key = { it.id }) { person ->
            PersonRow(
                person = person,
                onClick = { onOpenPerson(person) },
                onDeleteClick = { onDeleteRequested(person) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = GiftySpacing.l + MONOGRAM_SIZE + GiftySpacing.l),
                color = GiftyTheme.colors.divider,
            )
        }
    }
}

@Composable
private fun PersonRow(person: Person, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    val colors = GiftyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.people_open), onClick = onClick)
            .padding(start = GiftySpacing.l, end = GiftySpacing.xs, top = GiftySpacing.m, bottom = GiftySpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(name = person.name)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = GiftySpacing.l),
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
private fun Monogram(name: String) {
    val initial = name.substring(0, name.offsetByCodePoints(0, 1)).uppercase()
    Box(
        modifier = Modifier
            .size(MONOGRAM_SIZE)
            .background(GiftyTheme.colors.surfaceContext, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Lora),
            color = GiftyTheme.colors.textPrimary,
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
    GiftyTheme {
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
