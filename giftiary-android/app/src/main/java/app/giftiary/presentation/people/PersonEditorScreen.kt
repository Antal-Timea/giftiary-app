package app.giftiary.presentation.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.presentation.components.DecoratedPage
import app.giftiary.presentation.components.GiftCutout
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.gifts.GiftPhotoCamera
import app.giftiary.presentation.gifts.GiftPhotoSources
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.util.UUID

@Composable
fun PersonEditorScreen(
    viewModel: PersonEditorViewModel,
    onClose: () -> Unit,
    onDeleted: () -> Unit = onClose,
    // Where a person just created goes next; without it the editor simply closes.
    onCreated: ((UUID) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        when (state.status) {
            EditorStatus.SAVED -> {
                val created = state.savedPersonId?.takeIf { state.isNewPerson }
                if (created != null && onCreated != null) onCreated(created) else onClose()
            }
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
        photo = PersonEditorPhotoActions(
            onPick = viewModel::onPhotoPicked,
            camera = GiftPhotoCamera(
                newTarget = viewModel::newCaptureTarget,
                onCaptured = viewModel::onPhotoCaptured,
                onCancelled = viewModel::onCaptureCancelled,
                onUnavailable = viewModel::onCameraUnavailable,
            ),
            onRemove = viewModel::onPhotoRemoved,
            onErrorShown = viewModel::onPhotoErrorShown,
        ),
    )
}

/** What the photo part of the editor reaches. */
class PersonEditorPhotoActions(
    val onPick: (String) -> Unit = {},
    val camera: GiftPhotoCamera = GiftPhotoCamera(newTarget = { null }, onCaptured = {}, onCancelled = {}, onUnavailable = {}),
    val onRemove: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)

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
    photo: PersonEditorPhotoActions = PersonEditorPhotoActions(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val photoErrorMessage = state.photoError?.let {
        stringResource(
            when (it) {
                PersonPhotoError.UNREADABLE -> R.string.gift_photo_unreadable
                PersonPhotoError.CAMERA_UNAVAILABLE -> R.string.gift_photo_camera_unavailable
                PersonPhotoError.UNAVAILABLE_ON_SAVE -> R.string.gift_photo_unavailable_on_save
            },
        )
    }
    LaunchedEffect(photoErrorMessage) {
        if (photoErrorMessage != null) {
            snackbarHostState.showSnackbar(photoErrorMessage)
            photo.onErrorShown()
        }
    }
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
            EditorStatus.EDITING, EditorStatus.SAVED, EditorStatus.DELETED -> DecoratedPage(top = GiftCutout.LARGE) {
                GiftPhotoSources(onPick = photo.onPick, camera = photo.camera) { choosePhoto ->
                    PersonForm(
                        state = state,
                        onBack = onBack,
                        onNameChange = onNameChange,
                        onRelationshipChange = onRelationshipChange,
                        onSave = onSave,
                        onDelete = deletion.onRequested,
                        onChoosePhoto = choosePhoto,
                        onRemovePhoto = photo.onRemove,
                        modifier = contentModifier,
                    )
                }
            }
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
 * The approved Add a person layout: the photo, then the name and relationship. Dates are
 * occasions, added from the person's page once it exists.
 */
@Composable
private fun PersonForm(
    state: PersonEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
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
        PersonPhotoField(
            state = state,
            enabled = canEdit,
            onChoose = onChoosePhoto,
            onRemove = onRemovePhoto,
            modifier = Modifier.padding(top = GiftiarySpacing.section),
        )
        Column(
            modifier = Modifier.padding(top = GiftiarySpacing.fieldGroup),
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
            enabled = state.status == EditorStatus.EDITING && !state.isDeleting && !state.photoBusy,
            loading = state.isSaving,
            trailingArrow = true,
        )
    }
}

/**
 * The person's avatar as it will be saved, with what can be done to it. The avatar itself is
 * decorative: the actions say whether there is a photo, and the name is in the field below.
 */
@Composable
private fun PersonPhotoField(
    state: PersonEditorUiState,
    enabled: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    val actionsEnabled = enabled && !state.photoBusy
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(name = state.name, photo = state.photo, size = EDITOR_AVATAR_SIZE)
        // Beside the avatar while they fit, under one another at large text.
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
            verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            GiftiarySecondaryButton(
                text = stringResource(if (state.photoPath == null) R.string.person_photo_add else R.string.person_photo_change),
                onClick = onChoose,
                enabled = actionsEnabled,
            )
            if (state.photoPath != null) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = actionsEnabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
                ) {
                    Text(stringResource(R.string.person_photo_remove), style = GiftiaryType.actionSmall)
                }
            }
            if (state.photoBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primaryAction, strokeWidth = 2.dp)
            }
        }
    }
}

private val EDITOR_AVATAR_SIZE = 88.dp

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
