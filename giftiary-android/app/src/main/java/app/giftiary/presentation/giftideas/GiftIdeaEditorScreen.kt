package app.giftiary.presentation.giftideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.Person
import app.giftiary.domain.usecase.GiftIdeaInputError
import app.giftiary.presentation.components.DecoratedPage
import app.giftiary.presentation.components.GiftCutout
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryDropdownField
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySupportButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.gifts.GiftPhotoCamera
import app.giftiary.presentation.gifts.GiftPhotoError
import app.giftiary.presentation.gifts.GiftPhotoSources
import app.giftiary.presentation.gifts.GiftPhotoTile
import app.giftiary.presentation.gifts.priceErrorText
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.util.UUID

@Composable
fun GiftIdeaEditorScreen(
    viewModel: GiftIdeaEditorViewModel,
    onClose: () -> Unit,
    onOpenPeople: () -> Unit,
    onDeleted: () -> Unit = onClose,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        when (state.status) {
            GiftIdeaEditorStatus.CLOSED -> onClose()
            GiftIdeaEditorStatus.DELETED -> onDeleted()
            else -> Unit
        }
    }
    GiftIdeaEditorScreen(
        state = state,
        actions = GiftIdeaEditorActions(
            onBack = onClose,
            onOpenPeople = onOpenPeople,
            onRecipientChange = viewModel::onRecipientChange,
            onOccasionChange = viewModel::onOccasionChange,
            onNameChange = viewModel::onNameChange,
            onStatusChange = viewModel::onIdeaStatusChange,
            onEstimatedPriceChange = viewModel::onEstimatedPriceChange,
            onActualPriceChange = viewModel::onActualPriceChange,
            onProductUrlChange = viewModel::onProductUrlChange,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::save,
            onWriteErrorShown = viewModel::onWriteErrorShown,
            onDeleteRequested = viewModel::onDeleteRequested,
            onDeleteDismissed = viewModel::onDeleteDismissed,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
            onPhotoPicked = viewModel::onPhotoPicked,
            onNewCaptureTarget = viewModel::newCaptureTarget,
            onPhotoCaptured = viewModel::onPhotoCaptured,
            onCaptureCancelled = viewModel::onCaptureCancelled,
            onCameraUnavailable = viewModel::onCameraUnavailable,
            onPhotoRemoved = viewModel::onPhotoRemoved,
            onPhotoErrorShown = viewModel::onPhotoErrorShown,
        ),
    )
}

/** Groups the editor's many callbacks so the screen signature stays readable. */
class GiftIdeaEditorActions(
    val onBack: () -> Unit,
    val onOpenPeople: () -> Unit,
    val onRecipientChange: (Person) -> Unit,
    val onOccasionChange: (Occasion?) -> Unit,
    val onNameChange: (String) -> Unit,
    val onStatusChange: (GiftIdeaStatus) -> Unit,
    val onEstimatedPriceChange: (String) -> Unit,
    val onActualPriceChange: (String) -> Unit,
    val onProductUrlChange: (String) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onWriteErrorShown: () -> Unit,
    // Only an existing idea offers deleting.
    val onDeleteRequested: () -> Unit = {},
    val onDeleteDismissed: () -> Unit = {},
    val onDeleteConfirmed: () -> Unit = {},
    val onPhotoPicked: (String) -> Unit = {},
    val onNewCaptureTarget: () -> String? = { null },
    val onPhotoCaptured: (String) -> Unit = {},
    val onCaptureCancelled: (String) -> Unit = {},
    val onCameraUnavailable: (String) -> Unit = {},
    val onPhotoRemoved: () -> Unit = {},
    val onPhotoErrorShown: () -> Unit = {},
)

@Composable
fun GiftIdeaEditorScreen(
    state: GiftIdeaEditorUiState,
    actions: GiftIdeaEditorActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val writeErrorMessage = state.writeError?.let {
        stringResource(
            when (it) {
                GiftIdeaWriteError.FAILED -> R.string.gift_idea_write_failed
                GiftIdeaWriteError.PERSON_UNAVAILABLE -> R.string.gift_person_unavailable
                GiftIdeaWriteError.OCCASION_UNAVAILABLE -> R.string.gift_occasion_unavailable
                GiftIdeaWriteError.DELETE_FAILED -> R.string.gift_delete_failed
                GiftIdeaWriteError.PHOTO_UNAVAILABLE -> R.string.gift_photo_unavailable_on_save
            },
        )
    }
    LaunchedEffect(writeErrorMessage) {
        if (writeErrorMessage != null) {
            snackbarHostState.showSnackbar(writeErrorMessage)
            actions.onWriteErrorShown()
        }
    }
    val photoErrorMessage = state.photoError?.let {
        stringResource(
            when (it) {
                GiftPhotoError.UNREADABLE -> R.string.gift_photo_unreadable
                GiftPhotoError.CAMERA_UNAVAILABLE -> R.string.gift_photo_camera_unavailable
            },
        )
    }
    LaunchedEffect(photoErrorMessage) {
        if (photoErrorMessage != null) {
            snackbarHostState.showSnackbar(photoErrorMessage)
            actions.onPhotoErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = GiftiaryTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            GiftIdeaEditorStatus.LOADING -> GiftiaryTaskStatus(null, actions.onBack, contentModifier)
            GiftIdeaEditorStatus.NOT_FOUND -> GiftiaryTaskStatus(stringResource(R.string.gift_idea_not_found), actions.onBack, contentModifier)
            GiftIdeaEditorStatus.LOAD_FAILED -> GiftiaryTaskStatus(stringResource(R.string.gift_idea_load_failed), actions.onBack, contentModifier)
            GiftIdeaEditorStatus.EDITING, GiftIdeaEditorStatus.CLOSED, GiftIdeaEditorStatus.DELETED ->
                DecoratedPage(top = GiftCutout.LARGE) {
                    GiftIdeaForm(state = state, actions = actions, modifier = contentModifier)
                }
        }
    }

    if (state.confirmingDelete) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.gift_idea_delete_dialog_title, state.savedName),
            message = stringResource(R.string.gift_idea_delete_dialog_message),
            confirmLabel = stringResource(R.string.gift_delete_confirm),
            onConfirm = actions.onDeleteConfirmed,
            onDismiss = actions.onDeleteDismissed,
        )
    }
}

/**
 * The approved Add gift idea: what, for whom and for which occasion, and the photo, then the
 * optional details. The photo stays outside the folded details so it is found at once. Status and
 * the actual price are real parts of an idea that the board does not show; they join the details.
 * Edit opens with the details shown.
 */
@Composable
private fun GiftIdeaForm(state: GiftIdeaEditorUiState, actions: GiftIdeaEditorActions, modifier: Modifier = Modifier) {
    GiftPhotoSources(
        onPick = actions.onPhotoPicked,
        camera = GiftPhotoCamera(
            newTarget = actions.onNewCaptureTarget,
            onCaptured = actions.onPhotoCaptured,
            onCancelled = actions.onCaptureCancelled,
            onUnavailable = actions.onCameraUnavailable,
        ),
    ) { choosePhoto ->
        GiftIdeaFormContent(state, actions, choosePhoto, modifier)
    }
}

@Composable
private fun GiftIdeaFormContent(
    state: GiftIdeaEditorUiState,
    actions: GiftIdeaEditorActions,
    onChoosePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canEdit = state.status == GiftIdeaEditorStatus.EDITING && !state.isBusy
    var detailsShown by rememberSaveable { mutableStateOf(!state.isNew) }
    // A price error is in the details: they open so it can be seen.
    val showDetails = detailsShown || state.estimatedPriceError != null || state.actualPriceError != null
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .giftiaryReadableWidth()
            .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter, bottom = GiftiarySpacing.xxl),
    ) {
        GiftiaryTaskHeader(
            title = stringResource(if (state.isNew) R.string.gift_idea_editor_add_title else R.string.gift_idea_editor_edit_title),
            subtitle = stringResource(if (state.isNew) R.string.gift_idea_editor_add_subtitle else R.string.gift_idea_editor_edit_subtitle),
            onBack = actions.onBack,
            menu = if (state.isNew) null else {
                {
                    OptionsMenu(R.drawable.ic_more, stringResource(R.string.task_more)) {
                        Option(stringResource(R.string.gift_idea_delete), R.drawable.ic_delete, actions.onDeleteRequested, enabled = canEdit)
                    }
                }
            },
        )
        if (state.needsPerson) {
            Text(
                text = stringResource(R.string.gift_idea_editor_needs_person),
                modifier = Modifier.padding(top = GiftiarySpacing.section),
                style = GiftiaryType.body,
                color = GiftiaryTheme.colors.textSecondary,
            )
            GiftiaryPrimaryButton(
                text = stringResource(R.string.gift_editor_go_to_people),
                onClick = actions.onOpenPeople,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GiftiarySpacing.l),
            )
            return@Column
        }

        Column(
            modifier = Modifier.padding(top = GiftiarySpacing.section),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            GiftiaryTextField(
                value = state.name,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.gift_idea_name),
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.gift_idea_name_hint),
                errorMessage = if (GiftIdeaInputError.NAME_REQUIRED in state.errors) {
                    stringResource(R.string.gift_idea_error_name_required)
                } else {
                    null
                },
                enabled = canEdit,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            GiftiaryDropdownField(
                label = stringResource(R.string.gift_idea_for),
                options = state.recipientOptions,
                selected = state.selectedRecipient,
                optionLabel = { it.name },
                onSelect = actions.onRecipientChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit,
                errorMessage = if (GiftIdeaInputError.RECIPIENT_REQUIRED in state.errors) {
                    stringResource(R.string.gift_idea_error_recipient_required)
                } else {
                    null
                },
                leadingIcon = state.selectedRecipient?.let { person -> { GiftiaryAvatar(name = person.name, size = 26.dp) } },
            )
            GiftiaryDropdownField(
                label = stringResource(R.string.gift_idea_occasion_optional),
                options = state.occasionOptions,
                selected = state.selectedOccasion,
                optionLabel = { occasion -> occasion?.let { occasionTitle(it) } ?: stringResource(R.string.gift_no_occasion) },
                placeholder = stringResource(R.string.gift_no_occasion),
                onSelect = actions.onOccasionChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit && state.recipientId != null,
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
            GiftPhotoTile(
                hasPhoto = state.photoPath != null,
                photo = state.photo,
                busy = state.photoBusy,
                enabled = canEdit,
                onChoose = onChoosePhoto,
                onRemove = actions.onPhotoRemoved,
                modifier = Modifier.fillMaxWidth(),
                description = stringResource(R.string.gift_idea_photo_description),
            )
        }

        GiftiarySupportButton(
            text = stringResource(if (showDetails) R.string.gift_editor_fewer_details else R.string.gift_editor_more_details),
            onClick = { detailsShown = !showDetails },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftiarySpacing.xl),
            leadingIcon = painterResource(if (showDetails) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down),
        )
        if (showDetails) {
            GiftIdeaDetails(state = state, actions = actions, enabled = canEdit, modifier = Modifier.padding(top = GiftiarySpacing.browseGutter))
        }

        GiftiaryPrimaryButton(
            text = stringResource(R.string.gift_idea_editor_save),
            onClick = actions.onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            enabled = state.status == GiftIdeaEditorStatus.EDITING && !state.isDeleting && !state.photoBusy,
            loading = state.isBusy && !state.isDeleting,
            trailingArrow = true,
        )
    }
}

@Composable
private fun GiftIdeaDetails(state: GiftIdeaEditorUiState, actions: GiftIdeaEditorActions, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.fieldGroup)) {
        Column(verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
            Text(text = stringResource(R.string.gift_idea_status), style = GiftiaryType.label, color = GiftiaryTheme.colors.textLabel)
            GiftIdeaStatusSelector(selected = state.ideaStatus, onSelect = actions.onStatusChange, enabled = enabled)
        }
        GiftiaryTextField(
            value = state.estimatedPrice,
            onValueChange = actions.onEstimatedPriceChange,
            label = stringResource(R.string.gift_idea_estimated_price),
            modifier = Modifier.fillMaxWidth(),
            errorMessage = state.estimatedPriceError?.let { priceErrorText(it, state.estimatedCurrencyCode) },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            // Ideas are kept in the app's one currency, so it is shown, not offered as a choice.
            suffix = state.estimatedCurrencyCode,
        )
        if (state.showsActualPrice) {
            GiftiaryTextField(
                value = state.actualPrice,
                onValueChange = actions.onActualPriceChange,
                label = stringResource(R.string.gift_idea_actual_price),
                modifier = Modifier.fillMaxWidth(),
                errorMessage = state.actualPriceError?.let { priceErrorText(it, state.actualCurrencyCode) },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                suffix = state.actualCurrencyCode,
            )
        }
        GiftiaryTextField(
            value = state.productUrl,
            onValueChange = actions.onProductUrlChange,
            label = stringResource(R.string.gift_product_url),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.gift_product_url_placeholder),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            leadingIcon = painterResource(R.drawable.ic_link),
        )
        GiftiaryTextField(
            value = state.note,
            onValueChange = actions.onNoteChange,
            label = stringResource(R.string.gift_note),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.gift_idea_note_placeholder),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = false,
            minLines = 3,
        )
    }
}

private val previewActions = GiftIdeaEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@PreviewLightDark
@Composable
private fun GiftIdeaEditorPreview() {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    GiftiaryTheme {
        GiftIdeaEditorScreen(
            state = GiftIdeaEditorUiState(
                isNew = true,
                status = GiftIdeaEditorStatus.EDITING,
                recipientId = anna.id,
                activePeople = listOf(anna),
                peopleLoaded = true,
                name = "Wool scarf",
                ideaStatus = GiftIdeaStatus.PURCHASED,
                estimatedPrice = "10000",
                actualPrice = "9900",
            ),
            actions = previewActions,
        )
    }
}
