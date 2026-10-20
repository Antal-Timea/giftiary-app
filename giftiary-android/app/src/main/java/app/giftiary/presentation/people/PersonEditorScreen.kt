package app.giftiary.presentation.people

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

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

    val title = if (state.isNewPerson) R.string.person_editor_add_title else R.string.person_editor_edit_title
    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = stringResource(title), onBack = onBack) },
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
            .giftiaryReadableWidth()
            .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.l),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
    ) {
        GiftiaryTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = stringResource(R.string.person_editor_name),
            modifier = Modifier.fillMaxWidth(),
            errorMessage = if (state.showNameRequired) stringResource(R.string.person_editor_name_required) else null,
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        GiftiaryTextField(
            value = state.relationship,
            onValueChange = onRelationshipChange,
            label = stringResource(R.string.person_editor_relationship),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.person_editor_relationship_hint),
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
        )
        GiftiaryPrimaryButton(
            text = stringResource(R.string.person_editor_save),
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftiarySpacing.s),
            enabled = state.status == EditorStatus.EDITING,
            loading = state.isSaving,
        )
    }
}

@PreviewLightDark
@Composable
private fun PersonEditorValidationPreview() {
    GiftiaryTheme {
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

@PreviewLightDark
@Composable
private fun PersonEditorSavingPreview() {
    GiftiaryTheme {
        PersonEditorScreen(
            state = PersonEditorUiState(
                isNewPerson = false,
                status = EditorStatus.EDITING,
                name = "Anna Kovács",
                relationship = "Sister",
                isSaving = true,
            ),
            onBack = {},
            onNameChange = {},
            onRelationshipChange = {},
            onSave = {},
            onSaveErrorShown = {},
        )
    }
}
