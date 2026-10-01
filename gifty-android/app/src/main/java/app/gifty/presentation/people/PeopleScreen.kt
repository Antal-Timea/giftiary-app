package app.gifty.presentation.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.model.Person
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.components.NavigateBackButton
import app.gifty.presentation.theme.GiftyTheme
import java.time.Instant
import java.util.UUID

@Composable
fun PeopleScreen(
    onBack: () -> Unit,
    onAddPerson: () -> Unit,
    onEditPerson: (UUID) -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PeopleScreen(
        state = state,
        onBack = onBack,
        onAddPerson = onAddPerson,
        onEditPerson = { onEditPerson(it.id) },
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onDeleteErrorShown = viewModel::onDeleteErrorShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    state: PeopleUiState,
    onBack: () -> Unit,
    onAddPerson: () -> Unit,
    onEditPerson: (Person) -> Unit,
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_title)) },
                navigationIcon = { NavigateBackButton(onClick = onBack) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPerson) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.people_add),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            PeopleContent.Loading -> LoadingContent(contentModifier)
            PeopleContent.LoadFailed -> MessageContent(stringResource(R.string.people_load_failed), contentModifier)
            is PeopleContent.Loaded -> if (content.people.isEmpty()) {
                EmptyPeople(contentModifier)
            } else {
                PeopleList(
                    people = content.people,
                    onEditPerson = onEditPerson,
                    onDeleteRequested = onDeleteRequested,
                    modifier = contentModifier,
                )
            }
        }
    }

    state.personPendingDeletion?.let { person ->
        DeletePersonDialog(person = person, onConfirm = onDeleteConfirmed, onDismiss = onDeleteDismissed)
    }
}

@Composable
private fun PeopleList(
    people: List<Person>,
    onEditPerson: (Person) -> Unit,
    onDeleteRequested: (Person) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Keeps the last row reachable above the floating action button.
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(people, key = { it.id }) { person ->
            PersonRow(
                person = person,
                onClick = { onEditPerson(person) },
                onDeleteClick = { onDeleteRequested(person) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun PersonRow(person: Person, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.people_edit), onClick = onClick),
        headlineContent = { Text(person.name) },
        supportingContent = person.relationship?.let { relationship -> { Text(relationship) } },
        trailingContent = {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.people_delete, person.name),
                )
            }
        },
    )
}

@Composable
private fun EmptyPeople(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.people_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.people_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeletePersonDialog(person: Person, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.people_delete_dialog_title, person.name)) },
        text = { Text(stringResource(R.string.people_delete_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.people_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun PeopleScreenPreview() {
    val now = Instant.EPOCH
    GiftyTheme {
        PeopleScreen(
            state = PeopleUiState(
                content = PeopleContent.Loaded(
                    listOf(
                        Person(UUID.randomUUID(), "Anna", "Sister", null, null, now, now),
                        Person(UUID.randomUUID(), "Peter", null, null, null, now, now),
                    ),
                ),
            ),
            onBack = {},
            onAddPerson = {},
            onEditPerson = {},
            onDeleteRequested = {},
            onDeleteConfirmed = {},
            onDeleteDismissed = {},
            onDeleteErrorShown = {},
        )
    }
}
