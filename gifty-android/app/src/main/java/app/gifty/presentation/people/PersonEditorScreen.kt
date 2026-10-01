package app.gifty.presentation.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.components.NavigateBackButton
import app.gifty.presentation.theme.GiftyTheme

@Composable
fun PersonEditorScreen(
    viewModel: PersonEditorViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        if (state.status == EditorStatus.SAVED) onClose()
    }
    PersonEditorScreen(
        state = state,
        onBack = onClose,
        onNameChange = viewModel::onNameChange,
        onRelationshipChange = viewModel::onRelationshipChange,
        onSave = viewModel::save,
        onSaveErrorShown = viewModel::onSaveErrorShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonEditorScreen(
    state: PersonEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onSave: () -> Unit,
    onSaveErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(R.string.person_editor_save_failed)
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbarHostState.showSnackbar(saveFailedMessage)
            onSaveErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val title = if (state.isNewPerson) {
                        R.string.person_editor_add_title
                    } else {
                        R.string.person_editor_edit_title
                    }
                    Text(stringResource(title))
                },
                navigationIcon = { NavigateBackButton(onClick = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            EditorStatus.LOADING -> LoadingContent(contentModifier)
            EditorStatus.NOT_FOUND -> MessageContent(stringResource(R.string.person_editor_not_found), contentModifier)
            EditorStatus.LOAD_FAILED -> MessageContent(stringResource(R.string.person_editor_load_failed), contentModifier)
            EditorStatus.EDITING, EditorStatus.SAVED -> PersonForm(
                state = state,
                onNameChange = onNameChange,
                onRelationshipChange = onRelationshipChange,
                onSave = onSave,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun PersonForm(
    state: PersonEditorUiState,
    onNameChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canEdit = state.status == EditorStatus.EDITING && !state.isSaving
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit,
            label = { Text(stringResource(R.string.person_editor_name)) },
            isError = state.showNameRequired,
            supportingText = if (state.showNameRequired) {
                { Text(stringResource(R.string.person_editor_name_required)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        OutlinedTextField(
            value = state.relationship,
            onValueChange = onRelationshipChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit,
            label = { Text(stringResource(R.string.person_editor_relationship)) },
            placeholder = { Text(stringResource(R.string.person_editor_relationship_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
        )
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit,
        ) {
            Text(stringResource(R.string.person_editor_save))
        }
    }
}

@Preview
@Composable
private fun PersonEditorScreenPreview() {
    GiftyTheme {
        PersonEditorScreen(
            state = PersonEditorUiState(
                isNewPerson = true,
                status = EditorStatus.EDITING,
                name = " ",
                showNameRequired = true,
            ),
            onBack = {},
            onNameChange = {},
            onRelationshipChange = {},
            onSave = {},
            onSaveErrorShown = {},
        )
    }
}
