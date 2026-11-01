package app.giftiary.presentation.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.Person
import app.giftiary.domain.usecase.GiftInputError
import app.giftiary.presentation.components.GiftiaryAdaptivePreviews
import app.giftiary.presentation.components.GiftiaryDateField
import app.giftiary.presentation.components.GiftiaryDropdownField
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.ProvideWidthClass
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.components.directionColor
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.components.giftiaryWideWidth
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable
fun GiftEditorScreen(
    viewModel: GiftEditorViewModel,
    onClose: () -> Unit,
    onOpenPeople: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        if (state.status == GiftEditorStatus.CLOSED) onClose()
    }
    GiftEditorScreen(
        state = state,
        actions = GiftEditorActions(
            onBack = onClose,
            onOpenPeople = onOpenPeople,
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
        ),
    )
}

/** Groups the editor's many callbacks so the screen signature stays readable. */
class GiftEditorActions(
    val onBack: () -> Unit,
    val onOpenPeople: () -> Unit,
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

    val title = if (state.isNew) R.string.gift_editor_add_title else R.string.gift_editor_edit_title
    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = stringResource(title), onBack = actions.onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            GiftEditorStatus.LOADING -> LoadingContent(contentModifier)
            GiftEditorStatus.NOT_FOUND -> MessageContent(stringResource(R.string.gift_not_found), contentModifier)
            GiftEditorStatus.LOAD_FAILED -> MessageContent(stringResource(R.string.gift_load_failed), contentModifier)
            GiftEditorStatus.EDITING, GiftEditorStatus.CLOSED ->
                GiftForm(state = state, actions = actions, widthClass = widthClass, modifier = contentModifier)
        }
    }
}

/**
 * One form at every width: the fields are declared once, and only their arrangement changes. A wide
 * window puts who, what and when on the left and the optional details on the right; the whole form
 * scrolls as one, so a focused field in either column is brought above the keyboard.
 */
@Composable
private fun GiftForm(
    state: GiftEditorUiState,
    actions: GiftEditorActions,
    widthClass: WidthClass,
    modifier: Modifier = Modifier,
) {
    val canEdit = state.status == GiftEditorStatus.EDITING && !state.isBusy
    val direction: @Composable () -> Unit = {
        DirectionSelector(selected = state.direction, onSelect = actions.onDirectionChange, enabled = canEdit)
    }
    val identityFields: @Composable () -> Unit = {
            GiftiaryDropdownField(
                label = giftPersonLabel(state.direction),
                options = state.personOptions,
                selected = state.selectedPerson,
                optionLabel = { it.name },
                onSelect = actions.onPersonChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit,
                errorMessage = when {
                    GiftInputError.RECIPIENT_REQUIRED in state.errors -> stringResource(R.string.gift_error_recipient_required)
                    GiftInputError.GIVER_REQUIRED in state.errors -> stringResource(R.string.gift_error_giver_required)
                    else -> null
                },
            )
            GiftiaryTextField(
                value = state.name,
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.gift_name),
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.gift_name_hint),
                errorMessage = if (GiftInputError.NAME_REQUIRED in state.errors) stringResource(R.string.gift_error_name_required) else null,
                enabled = canEdit,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            GiftiaryDropdownField(
                label = stringResource(R.string.gift_occasion),
                options = state.occasionOptions,
                selected = state.selectedOccasion,
                optionLabel = { occasion -> occasion?.let { occasionTitle(it) } ?: stringResource(R.string.gift_no_occasion) },
                onSelect = actions.onOccasionChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit && state.personId != null,
            )
            GiftiaryDateField(
                label = stringResource(R.string.gift_date),
                date = state.givenAt,
                onDateChange = actions.onDateChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit,
            )
    }
    val detailFields: @Composable () -> Unit = {
            GiftiaryTextField(
                value = state.price,
                onValueChange = actions.onPriceChange,
                label = stringResource(R.string.gift_price_optional),
                modifier = Modifier.fillMaxWidth(),
                errorMessage = state.priceError?.let { priceErrorText(it, state.currencyCode) },
                enabled = canEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                suffix = state.currencyCode,
            )
            GiftiaryTextField(
                value = state.productUrl,
                onValueChange = actions.onProductUrlChange,
                label = stringResource(R.string.gift_product_url_optional),
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            GiftiaryTextField(
                value = state.note,
                onValueChange = actions.onNoteChange,
                label = stringResource(R.string.gift_note_optional),
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = false,
                minLines = 3,
            )
            GiftPhotoField(
                hasPhoto = state.photoPath != null,
                photo = state.photo,
                busy = state.photoBusy,
                enabled = canEdit,
                onPick = actions.onPhotoPicked,
                camera = GiftPhotoCamera(
                    newTarget = actions.onNewCaptureTarget,
                    onCaptured = actions.onPhotoCaptured,
                    onCancelled = actions.onCaptureCancelled,
                    onUnavailable = actions.onCameraUnavailable,
                ),
                onRemove = actions.onPhotoRemoved,
                modifier = Modifier.fillMaxWidth(),
            )
    }
    // A lone message and button stay one column even on a wide window.
    val twoColumns = widthClass == WidthClass.EXPANDED && !state.needsPerson
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .then(if (twoColumns) Modifier.giftiaryWideWidth() else Modifier.giftiaryReadableWidth())
            .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.l),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
    ) {
        when {
            state.needsPerson -> {
                direction()
                NeedsPerson(onOpenPeople = actions.onOpenPeople)
            }
            twoColumns -> {
                Row(horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.xxl)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l)) {
                        direction()
                        identityFields()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l)) {
                        detailFields()
                    }
                }
                SaveButton(state = state, onSave = actions.onSave, modifier = Modifier.align(Alignment.End))
            }
            else -> {
                direction()
                identityFields()
                detailFields()
                SaveButton(state = state, onSave = actions.onSave, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SaveButton(state: GiftEditorUiState, onSave: () -> Unit, modifier: Modifier = Modifier) {
    GiftiaryPrimaryButton(
        text = stringResource(R.string.gift_editor_save),
        onClick = onSave,
        modifier = modifier.padding(top = GiftiarySpacing.s),
        enabled = state.status == GiftEditorStatus.EDITING,
        loading = state.isBusy,
    )
}

@Composable
private fun DirectionSelector(selected: GiftDirection, onSelect: (GiftDirection) -> Unit, enabled: Boolean) {
    val colors = GiftiaryTheme.colors
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        GiftDirection.entries.forEachIndexed { index, direction ->
            SegmentedButton(
                selected = direction == selected,
                onClick = { onSelect(direction) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = GiftDirection.entries.size),
                enabled = enabled,
                icon = {
                    Icon(
                        painter = painterResource(
                            if (direction == GiftDirection.GIVEN) R.drawable.ic_gift_given else R.drawable.ic_gift_received,
                        ),
                        contentDescription = null,
                        tint = directionColor(direction),
                    )
                },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.surfaceContext,
                    activeContentColor = colors.textPrimary,
                    activeBorderColor = colors.border,
                    inactiveContainerColor = colors.background,
                    inactiveContentColor = colors.textSecondary,
                    inactiveBorderColor = colors.border,
                ),
            ) {
                Text(giftDirectionLabel(direction))
            }
        }
    }
}

@Composable
private fun NeedsPerson(onOpenPeople: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l)) {
        Text(
            text = stringResource(R.string.gift_editor_needs_person),
            style = MaterialTheme.typography.bodyLarge,
            color = GiftiaryTheme.colors.textSecondary,
        )
        GiftiaryPrimaryButton(
            text = stringResource(R.string.gift_editor_go_to_people),
            onClick = onOpenPeople,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val previewActions = GiftEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

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
