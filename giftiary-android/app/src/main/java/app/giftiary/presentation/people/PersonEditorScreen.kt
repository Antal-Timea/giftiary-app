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
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

@Composable
fun PersonEditorScreen(
    viewModel: PersonEditorViewModel,
    onClose: () -> Unit,
    onDeleted: () -> Unit = onClose,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        when (state.status) {
            EditorStatus.SAVED -> onClose()
            EditorStatus.DELETED -> onDeleted()
            else -> Unit
        }
    }
    PersonEditorScreen(
        state = state,
        onBack = onClose,
        onNameChange = viewModel::onNameChange,
        onRelationshipChange = viewModel::onRelationshipChange,
        onSave = viewModel::save,
        onSaveErrorShown = viewModel::onSaveErrorShown,
        deletion = PersonEditorDeletion(
            onRequested = viewModel::onDeleteRequested,
            onDismissed = viewModel::onDeleteDismissed,
            onConfirmed = viewModel::onDeleteConfirmed,
            onErrorShown = viewModel::onDeleteErrorShown,
        ),
    )
}

/** What deleting from the editor reaches; only an existing person offers it. */
class PersonEditorDeletion(
    val onRequested: () -> Unit,
    val onDismissed: () -> Unit,
    val onConfirmed: () -> Unit,
    val onErrorShown: () -> Unit,
)

@Composable
fun PersonEditorScreen(
    state: PersonEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onSave: () -> Unit,
    onSaveErrorShown: () -> Unit,
    deletion: PersonEditorDeletion,
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
    val deleteFailedMessage = stringResource(R.string.people_delete_failed)
    LaunchedEffect(state.deleteFailed) {
        if (state.deleteFailed) {
            snackbarHostState.showSnackbar(deleteFailedMessage)
            deletion.onErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = GiftiaryTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            EditorStatus.LOADING -> GiftiaryTaskStatus(null, onBack, contentModifier)
            EditorStatus.NOT_FOUND -> GiftiaryTaskStatus(stringResource(R.string.person_editor_not_found), onBack, contentModifier)
            EditorStatus.LOAD_FAILED -> GiftiaryTaskStatus(stringResource(R.string.person_editor_load_failed), onBack, contentModifier)
            EditorStatus.EDITING, EditorStatus.SAVED, EditorStatus.DELETED -> PersonForm(
                state = state,
                onBack = onBack,
                onNameChange = onNameChange,
                onRelationshipChange = onRelationshipChange,
                onSave = onSave,
                onDelete = deletion.onRequested,
                modifier = contentModifier,
            )
        }
    }

    if (state.confirmingDelete) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.people_delete_dialog_title, state.savedName),
            message = stringResource(R.string.people_delete_dialog_message),
            confirmLabel = stringResource(R.string.people_delete_confirm),
            onConfirm = deletion.onConfirmed,
            onDismiss = deletion.onDismissed,
        )
    }
}

/**
 * The approved Add a person layout, less what a person cannot hold yet: there is no person photo,
 * so no photo picker or pretend upload, and dates are occasions, added from the person's page once
 * they exist.
 */
@Composable
private fun PersonForm(
    state: PersonEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canEdit = state.status == EditorStatus.EDITING && !state.isBusy
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .giftiaryReadableWidth()
            .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter, bottom = GiftiarySpacing.xxl),
    ) {
        GiftiaryTaskHeader(
            title = stringResource(if (state.isNewPerson) R.string.person_editor_add_title else R.string.person_editor_edit_title),
            subtitle = stringResource(if (state.isNewPerson) R.string.person_editor_add_subtitle else R.string.person_editor_edit_subtitle),
            onBack = onBack,
            menu = if (state.isNewPerson) null else {
                {
                    OptionsMenu(R.drawable.ic_more, stringResource(R.string.task_more)) {
                        Option(stringResource(R.string.people_delete, state.savedName), R.drawable.ic_delete, onDelete, enabled = canEdit)
                    }
                }
            },
        )
        Column(
            modifier = Modifier.padding(top = GiftiarySpacing.section),
            verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.fieldGroup),
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
        }
        GiftiaryPrimaryButton(
            text = stringResource(R.string.person_editor_save),
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftiarySpacing.xxl),
            enabled = state.status == EditorStatus.EDITING && !state.isDeleting,
            loading = state.isSaving,
            trailingArrow = true,
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
            deletion = PersonEditorDeletion({}, {}, {}, {}),
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
            deletion = PersonEditorDeletion({}, {}, {}, {}),
        )
    }
}
