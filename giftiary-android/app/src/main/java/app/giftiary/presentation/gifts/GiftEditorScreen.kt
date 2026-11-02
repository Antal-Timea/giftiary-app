package app.giftiary.presentation.gifts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.Person
import app.giftiary.domain.usecase.GiftInputError
import app.giftiary.presentation.components.GiftiaryAdaptivePreviews
import app.giftiary.presentation.components.GiftiaryChoicePill
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryDateField
import app.giftiary.presentation.components.GiftiaryDropdownField
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiarySupportButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.ProvideWidthClass
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.components.arrow
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.components.giftiaryWideWidth
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable
fun GiftEditorScreen(
    viewModel: GiftEditorViewModel,
    onClose: () -> Unit,
    onOpenPeople: () -> Unit,
    onAddPerson: () -> Unit,
    onDeleted: () -> Unit = onClose,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        when (state.status) {
            GiftEditorStatus.CLOSED -> onClose()
            GiftEditorStatus.DELETED -> onDeleted()
            else -> Unit
        }
    }
    GiftEditorScreen(
        state = state,
        actions = GiftEditorActions(
            onBack = onClose,
            onOpenPeople = onOpenPeople,
            onAddPerson = onAddPerson,
            onDirectionChange = viewModel::onDirectionChange,
            onPersonChange = viewModel::onPersonChange,
            onOccasionChange = viewModel::onOccasionChange,
            onNameChange = viewModel::onNameChange,
            onDateChange = viewModel::onDateChange,
            onPriceChange = viewModel::onPriceChange,
            onProductUrlChange = viewModel::onProductUrlChange,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::save,
            onWriteErrorShown = viewModel::onWriteErrorShown,
            onPhotoPicked = viewModel::onPhotoPicked,
            onNewCaptureTarget = viewModel::newCaptureTarget,
            onPhotoCaptured = viewModel::onPhotoCaptured,
            onCaptureCancelled = viewModel::onCaptureCancelled,
            onCameraUnavailable = viewModel::onCameraUnavailable,
            onPhotoRemoved = viewModel::onPhotoRemoved,
            onPhotoErrorShown = viewModel::onPhotoErrorShown,
            onDeleteRequested = viewModel::onDeleteRequested,
            onDeleteDismissed = viewModel::onDeleteDismissed,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
        ),
    )
}

/** Groups the editor's many callbacks so the screen signature stays readable. */
class GiftEditorActions(
    val onBack: () -> Unit,
    val onOpenPeople: () -> Unit,
    val onAddPerson: () -> Unit,
    val onDirectionChange: (GiftDirection) -> Unit,
    val onPersonChange: (Person) -> Unit,
    val onOccasionChange: (Occasion?) -> Unit,
    val onNameChange: (String) -> Unit,
    val onDateChange: (LocalDate) -> Unit,
    val onPriceChange: (String) -> Unit,
    val onProductUrlChange: (String) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onWriteErrorShown: () -> Unit,
    val onPhotoPicked: (String) -> Unit = {},
    val onNewCaptureTarget: () -> String? = { null },
    val onPhotoCaptured: (String) -> Unit = {},
    val onCaptureCancelled: (String) -> Unit = {},
    val onCameraUnavailable: (String) -> Unit = {},
    val onPhotoRemoved: () -> Unit = {},
    val onPhotoErrorShown: () -> Unit = {},
    // Only an existing gift offers deleting.
    val onDeleteRequested: () -> Unit = {},
    val onDeleteDismissed: () -> Unit = {},
    val onDeleteConfirmed: () -> Unit = {},
)

@Composable
fun GiftEditorScreen(
    state: GiftEditorUiState,
    actions: GiftEditorActions,
    modifier: Modifier = Modifier,
    widthClass: WidthClass = LocalWidthClass.current,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val writeErrorMessage = state.writeError?.let {
        stringResource(
            when (it) {
                GiftWriteError.FAILED -> R.string.gift_write_failed
                GiftWriteError.PERSON_UNAVAILABLE -> R.string.gift_person_unavailable
                GiftWriteError.OCCASION_UNAVAILABLE -> R.string.gift_occasion_unavailable
                GiftWriteError.PHOTO_UNAVAILABLE -> R.string.gift_photo_unavailable_on_save
                GiftWriteError.DELETE_FAILED -> R.string.gift_delete_failed
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
            GiftEditorStatus.LOADING -> GiftiaryTaskStatus(null, actions.onBack, contentModifier)
            GiftEditorStatus.NOT_FOUND -> GiftiaryTaskStatus(stringResource(R.string.gift_not_found), actions.onBack, contentModifier)
            GiftEditorStatus.LOAD_FAILED -> GiftiaryTaskStatus(stringResource(R.string.gift_load_failed), actions.onBack, contentModifier)
            GiftEditorStatus.EDITING, GiftEditorStatus.CLOSED, GiftEditorStatus.DELETED ->
                GiftForm(state = state, actions = actions, widthClass = widthClass, modifier = contentModifier)
        }
    }

    if (state.confirmingDelete) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.gift_delete_dialog_title, state.savedName),
            message = stringResource(R.string.gift_delete_dialog_message),
            confirmLabel = stringResource(R.string.gift_delete_confirm),
            onConfirm = actions.onDeleteConfirmed,
            onDismiss = actions.onDeleteDismissed,
        )
    }
}

/**
 * The approved quick capture: three numbered steps and Save, with the optional details one tap
 * away. Edit opens with the details shown, so a gift's photo and price are where the user left
 * them. Values live in the view model, so showing or hiding the details never loses them.
 *
 * A wide window puts the steps on the left and the details, always shown, on the right; the whole
 * form scrolls as one, so a focused field in either column is brought above the keyboard.
 */
@Composable
private fun GiftForm(
    state: GiftEditorUiState,
    actions: GiftEditorActions,
    widthClass: WidthClass,
    modifier: Modifier = Modifier,
) {
    val canEdit = state.status == GiftEditorStatus.EDITING && !state.isBusy
    var detailsShown by rememberSaveable { mutableStateOf(!state.isNew) }
    // Kept here, above the layout switch, so a resize does not clear the search.
    var personQuery by rememberSaveable { mutableStateOf("") }
    // A lone message and button stay one column even on a wide window.
    val twoColumns = widthClass == WidthClass.EXPANDED && !state.needsPerson
    // A price error is in the details: they open so it can be seen.
    val showDetails = twoColumns || detailsShown || state.priceError != null

    GiftPhotoSources(
        onPick = actions.onPhotoPicked,
        camera = GiftPhotoCamera(
            newTarget = actions.onNewCaptureTarget,
            onCaptured = actions.onPhotoCaptured,
            onCancelled = actions.onCaptureCancelled,
            onUnavailable = actions.onCameraUnavailable,
        ),
    ) { choosePhoto ->
        val steps: @Composable () -> Unit = {
            GiftSteps(
                state = state,
                actions = actions,
                personQuery = personQuery,
                onPersonQueryChange = { personQuery = it },
                enabled = canEdit,
            )
        }
        val details: @Composable () -> Unit = {
            GiftDetails(state = state, actions = actions, onChoosePhoto = choosePhoto, enabled = canEdit)
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .then(if (twoColumns) Modifier.giftiaryWideWidth() else Modifier.giftiaryReadableWidth())
                .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter, bottom = GiftiarySpacing.xxl),
        ) {
            GiftiaryTaskHeader(
                title = stringResource(if (state.isNew) R.string.gift_editor_add_title else R.string.gift_editor_edit_title),
                subtitle = stringResource(if (state.isNew) R.string.gift_editor_add_subtitle else R.string.gift_editor_edit_subtitle),
                onBack = actions.onBack,
                menu = if (state.isNew) null else {
                    {
                        OptionsMenu(R.drawable.ic_more, stringResource(R.string.task_more)) {
                            Option(stringResource(R.string.gift_delete), R.drawable.ic_delete, actions.onDeleteRequested, enabled = canEdit)
                        }
                    }
                },
            )
            when {
                state.needsPerson -> NeedsPerson(onOpenPeople = actions.onOpenPeople)
                twoColumns -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.xxl)) {
                        Column(Modifier.weight(1f)) { steps() }
                        Column(Modifier.weight(1f).padding(top = GiftiarySpacing.section)) { details() }
                    }
                    SaveButton(state, actions.onSave, Modifier.align(Alignment.End).padding(top = GiftiarySpacing.xxl))
                }
                showDetails -> {
                    steps()
                    DetailsToggle(shown = true, onToggle = { detailsShown = false }, Modifier.padding(top = GiftiarySpacing.xxl))
                    Column(Modifier.padding(top = GiftiarySpacing.browseGutter)) { details() }
                    SaveButton(state, actions.onSave, Modifier.fillMaxWidth().padding(top = 28.dp))
                }
                else -> {
                    steps()
                    SaveButton(state, actions.onSave, Modifier.fillMaxWidth().padding(top = GiftiarySpacing.xxl))
                    DetailsToggle(shown = false, onToggle = { detailsShown = true }, Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

/** Given or received, who, and what: everything a gift needs. */
@Composable
private fun GiftSteps(
    state: GiftEditorUiState,
    actions: GiftEditorActions,
    personQuery: String,
    onPersonQueryChange: (String) -> Unit,
    enabled: Boolean,
) {
    StepHeading(number = 1, title = stringResource(R.string.gift_editor_step_direction), modifier = Modifier.padding(top = GiftiarySpacing.section))
    Row(
        modifier = Modifier
            .padding(top = GiftiarySpacing.m)
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GiftDirection.entries.forEach { direction ->
            GiftiaryChoicePill(
                text = stringResource(if (direction == GiftDirection.GIVEN) R.string.gift_editor_i_gave else R.string.gift_editor_i_received),
                selected = state.direction == direction,
                onClick = { actions.onDirectionChange(direction) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                leadingIcon = painterResource(direction.arrow),
            )
        }
    }

    StepHeading(
        number = 2,
        title = stringResource(
            if (state.direction == GiftDirection.GIVEN) R.string.gift_editor_step_person_given else R.string.gift_editor_step_person_received,
        ),
        modifier = Modifier.padding(top = GiftiarySpacing.section),
    )
    GiftPersonChooser(
        people = state.personOptions,
        selectedId = state.personId,
        query = personQuery,
        onQueryChange = onPersonQueryChange,
        onSelect = actions.onPersonChange,
        onAddPerson = actions.onAddPerson,
        enabled = enabled,
        errorMessage = when {
            GiftInputError.RECIPIENT_REQUIRED in state.errors -> stringResource(R.string.gift_error_recipient_required)
            GiftInputError.GIVER_REQUIRED in state.errors -> stringResource(R.string.gift_error_giver_required)
            else -> null
        },
        modifier = Modifier.padding(top = GiftiarySpacing.m),
    )

    val nameStep = stringResource(R.string.gift_editor_step_name)
    StepHeading(number = 3, title = nameStep, modifier = Modifier.padding(top = GiftiarySpacing.section))
    GiftiaryTextField(
        value = state.name,
        onValueChange = actions.onNameChange,
        // The step above is the field's label; it is also what the field is called when read out.
        label = null,
        modifier = Modifier
            .padding(top = GiftiarySpacing.m)
            .fillMaxWidth()
            .semantics { contentDescription = nameStep },
        placeholder = stringResource(R.string.gift_editor_name_placeholder),
        errorMessage = if (GiftInputError.NAME_REQUIRED in state.errors) stringResource(R.string.gift_error_name_required) else null,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
    )
    Text(
        text = stringResource(R.string.gift_editor_name_examples),
        modifier = Modifier.padding(top = GiftiarySpacing.s),
        style = GiftiaryType.metadata.copy(fontSize = 11.5.sp),
        color = GiftiaryTheme.colors.textTertiary,
    )
}

/**
 * The optional details, in the board's order. The product link is not on the board but is a real
 * part of a gift, so it follows the note as the last, quietest field.
 */
@Composable
private fun GiftDetails(state: GiftEditorUiState, actions: GiftEditorActions, onChoosePhoto: () -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.fieldGroup)) {
        GiftiaryDropdownField(
            label = stringResource(R.string.gift_occasion),
            options = state.occasionOptions,
            selected = state.selectedOccasion,
            optionLabel = { occasion -> occasion?.let { occasionTitle(it) } ?: stringResource(R.string.gift_no_occasion) },
            placeholder = stringResource(R.string.gift_no_occasion),
            onSelect = actions.onOccasionChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && state.personId != null,
        )
        GiftiaryDateField(
            label = stringResource(R.string.gift_date),
            date = state.givenAt,
            onDateChange = actions.onDateChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        GiftiaryTextField(
            value = state.price,
            onValueChange = actions.onPriceChange,
            label = stringResource(R.string.gift_price),
            modifier = Modifier.fillMaxWidth(),
            errorMessage = state.priceError?.let { priceErrorText(it, state.currencyCode) },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            // Gifts are kept in the app's one currency, so it is shown, not offered as a choice.
            suffix = state.currencyCode,
        )
        GiftPhotoTile(
            hasPhoto = state.photoPath != null,
            photo = state.photo,
            busy = state.photoBusy,
            enabled = enabled,
            onChoose = onChoosePhoto,
            onRemove = actions.onPhotoRemoved,
            modifier = Modifier.fillMaxWidth(),
        )
        GiftiaryTextField(
            value = state.note,
            onValueChange = actions.onNoteChange,
            label = stringResource(R.string.gift_note),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.gift_note_placeholder),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = false,
            minLines = 3,
        )
        GiftiaryTextField(
            value = state.productUrl,
            onValueChange = actions.onProductUrlChange,
            label = stringResource(R.string.gift_product_url),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.gift_product_url_placeholder),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            leadingIcon = painterResource(R.drawable.ic_link),
        )
    }
}

/** A numbered step: the number is read as "Step 1 of 3", so it means something without sight. */
@Composable
private fun StepHeading(number: Int, title: String, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    val position = stringResource(R.string.gift_editor_step, number, GIFT_STEPS)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(colors.support, GiftiaryShapes.pill)
                .clearAndSetSemantics { contentDescription = position },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = GiftiaryType.chip.copy(fontSize = 12.sp), color = colors.onSupport)
        }
        Text(text = title, style = GiftiaryType.sectionTitle.copy(fontSize = 18.sp), color = colors.textPrimary)
    }
}

@Composable
private fun DetailsToggle(shown: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    GiftiarySupportButton(
        text = stringResource(if (shown) R.string.gift_editor_fewer_details else R.string.gift_editor_more_details),
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        leadingIcon = painterResource(if (shown) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down),
    )
}

@Composable
private fun SaveButton(state: GiftEditorUiState, onSave: () -> Unit, modifier: Modifier = Modifier) {
    GiftiaryPrimaryButton(
        text = stringResource(R.string.gift_editor_save),
        onClick = onSave,
        modifier = modifier,
        enabled = state.status == GiftEditorStatus.EDITING && !state.isDeleting,
        loading = state.isBusy && !state.isDeleting,
        trailingArrow = true,
    )
}

@Composable
private fun NeedsPerson(onOpenPeople: () -> Unit) {
    Column(
        modifier = Modifier.padding(top = GiftiarySpacing.section),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
    ) {
        Text(
            text = stringResource(R.string.gift_editor_needs_person),
            style = GiftiaryType.body,
            color = GiftiaryTheme.colors.textSecondary,
        )
        GiftiaryPrimaryButton(
            text = stringResource(R.string.gift_editor_go_to_people),
            onClick = onOpenPeople,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val GIFT_STEPS = 3

private val previewActions = GiftEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@PreviewLightDark
@GiftiaryAdaptivePreviews
@Composable
private fun GiftEditorPreview() {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    GiftiaryTheme {
        ProvideWidthClass {
            GiftEditorPreviewContent(anna)
        }
    }
}

@Composable
private fun GiftEditorPreviewContent(anna: Person) {
    GiftEditorScreen(
        state = GiftEditorUiState(
            isNew = true,
            status = GiftEditorStatus.EDITING,
            givenAt = LocalDate.of(2026, 3, 12),
            direction = GiftDirection.RECEIVED,
            personId = anna.id,
            activePeople = listOf(anna),
            peopleLoaded = true,
            price = "12,505",
            currencyCode = "HUF",
            errors = setOf(GiftInputError.NAME_REQUIRED),
            priceError = PriceError.TOO_PRECISE,
        ),
        actions = previewActions,
    )
}
