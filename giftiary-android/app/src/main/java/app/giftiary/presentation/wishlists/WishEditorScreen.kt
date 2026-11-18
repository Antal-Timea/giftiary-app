package app.giftiary.presentation.wishlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import app.giftiary.presentation.components.DecoratedPage
import app.giftiary.presentation.components.GiftCutout
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryInput
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
import app.giftiary.presentation.gifts.PriceError
import app.giftiary.presentation.gifts.priceErrorText
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

@Composable
fun WishEditorScreen(
    viewModel: WishEditorViewModel,
    onClose: () -> Unit,
    onDeleted: () -> Unit = onClose,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        when (state.status) {
            WishEditorStatus.SAVED -> onClose()
            WishEditorStatus.DELETED -> onDeleted()
            else -> Unit
        }
    }
    WishEditorScreen(
        state = state,
        actions = WishEditorActions(
            onBack = onClose,
            onNameChange = viewModel::onNameChange,
            onPriceChange = viewModel::onPriceChange,
            onProductUrlChange = viewModel::onProductUrlChange,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::save,
            onSaveErrorShown = viewModel::onSaveErrorShown,
            onDeleteRequested = viewModel::onDeleteRequested,
            onDeleteDismissed = viewModel::onDeleteDismissed,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
            onDeleteErrorShown = viewModel::onDeleteErrorShown,
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

/** Groups the editor's callbacks so the screen signature stays readable. */
class WishEditorActions(
    val onBack: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onPriceChange: (String) -> Unit,
    val onProductUrlChange: (String) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onSaveErrorShown: () -> Unit,
    // Only an existing wish offers deleting.
    val onDeleteRequested: () -> Unit = {},
    val onDeleteDismissed: () -> Unit = {},
    val onDeleteConfirmed: () -> Unit = {},
    val onDeleteErrorShown: () -> Unit = {},
    val onPhotoPicked: (String) -> Unit = {},
    val onNewCaptureTarget: () -> String? = { null },
    val onPhotoCaptured: (String) -> Unit = {},
    val onCaptureCancelled: (String) -> Unit = {},
    val onCameraUnavailable: (String) -> Unit = {},
    val onPhotoRemoved: () -> Unit = {},
    val onPhotoErrorShown: () -> Unit = {},
)

@Composable
fun WishEditorScreen(state: WishEditorUiState, actions: WishEditorActions, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = when {
        state.photoUnavailableOnSave -> stringResource(R.string.gift_photo_unavailable_on_save)
        state.saveFailed -> stringResource(R.string.wish_save_failed)
        else -> null
    }
    LaunchedEffect(saveFailedMessage) {
        if (saveFailedMessage != null) {
            snackbarHostState.showSnackbar(saveFailedMessage)
            actions.onSaveErrorShown()
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
    val deleteFailedMessage = stringResource(R.string.wish_delete_failed)
    LaunchedEffect(state.deleteFailed) {
        if (state.deleteFailed) {
            snackbarHostState.showSnackbar(deleteFailedMessage)
            actions.onDeleteErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = GiftiaryTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            WishEditorStatus.LOADING -> GiftiaryTaskStatus(null, actions.onBack, contentModifier)
            WishEditorStatus.NOT_FOUND -> GiftiaryTaskStatus(stringResource(R.string.wish_not_found), actions.onBack, contentModifier)
            WishEditorStatus.WISHLIST_NOT_FOUND -> GiftiaryTaskStatus(stringResource(R.string.wishlist_not_found), actions.onBack, contentModifier)
            WishEditorStatus.LOAD_FAILED -> GiftiaryTaskStatus(stringResource(R.string.wish_load_failed), actions.onBack, contentModifier)
            WishEditorStatus.EDITING, WishEditorStatus.SAVED, WishEditorStatus.DELETED ->
                DecoratedPage(top = GiftCutout.LARGE) {
                    WishForm(state = state, actions = actions, modifier = contentModifier)
                }
        }
    }

    if (state.confirmingDelete) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.wish_delete_dialog_title, state.savedName),
            message = stringResource(R.string.wish_delete_dialog_message),
            confirmLabel = stringResource(R.string.wishlist_delete_confirm),
            onConfirm = actions.onDeleteConfirmed,
            onDismiss = actions.onDeleteDismissed,
        )
    }
}

/**
 * The approved Add / Edit wish: the list, what is wished for, then the optional details one tap
 * away. A wish stays on the list it was added to, so the list is shown but cannot be changed. The
 * price keeps the wish's own currency, as there is no currency choice. Edit opens with the details
 * shown.
 */
@Composable
private fun WishForm(state: WishEditorUiState, actions: WishEditorActions, modifier: Modifier = Modifier) {
    val canEdit = state.status == WishEditorStatus.EDITING && !state.isBusy
    var detailsShown by rememberSaveable { mutableStateOf(!state.isNew) }
    // A price error is in the details: they open so it can be seen.
    val showDetails = detailsShown || state.priceError != null
    GiftPhotoSources(
        onPick = actions.onPhotoPicked,
        camera = GiftPhotoCamera(
            newTarget = actions.onNewCaptureTarget,
            onCaptured = actions.onPhotoCaptured,
            onCancelled = actions.onCaptureCancelled,
            onUnavailable = actions.onCameraUnavailable,
        ),
    ) { choosePhoto ->
        WishFormContent(state, actions, canEdit, showDetails, { detailsShown = !showDetails }, choosePhoto, modifier)
    }
}

@Composable
private fun WishFormContent(
    state: WishEditorUiState,
    actions: WishEditorActions,
    canEdit: Boolean,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    onChoosePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .giftiaryReadableWidth()
            .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter, bottom = GiftiarySpacing.xxl),
    ) {
        GiftiaryTaskHeader(
            title = stringResource(if (state.isNew) R.string.wish_editor_add_title else R.string.wish_editor_edit_title),
            subtitle = stringResource(if (state.isNew) R.string.wish_editor_add_subtitle else R.string.wish_editor_edit_subtitle),
            onBack = actions.onBack,
            menu = if (state.isNew) null else {
                {
                    OptionsMenu(R.drawable.ic_more, stringResource(R.string.task_more)) {
                        Option(stringResource(R.string.wish_delete), R.drawable.ic_delete, actions.onDeleteRequested, enabled = canEdit)
                    }
                }
            },
        )
        state.wishlistName?.let { name ->
            GiftiaryInput(
                value = name,
                onValueChange = {},
                label = stringResource(R.string.wish_editor_wishlist),
                modifier = Modifier
                    .padding(top = GiftiarySpacing.section)
                    .fillMaxWidth(),
                readOnly = true,
                leadingIcon = { GiftiaryAvatar(name = name, size = 26.dp) },
            )
        }

        val nameStep = stringResource(R.string.wish_editor_step_name)
        Text(
            text = nameStep,
            modifier = Modifier
                .padding(top = GiftiarySpacing.section)
                .semantics { heading() },
            style = GiftiaryType.sectionTitle.copy(fontSize = 18.sp),
            color = GiftiaryTheme.colors.textPrimary,
        )
        GiftiaryTextField(
            value = state.name,
            onValueChange = actions.onNameChange,
            // The heading above is the field's label; it is also what the field is called when read out.
            label = null,
            modifier = Modifier
                .padding(top = GiftiarySpacing.m)
                .fillMaxWidth()
                .semantics { contentDescription = nameStep },
            placeholder = stringResource(R.string.wish_editor_name_hint),
            errorMessage = if (state.showNameRequired) stringResource(R.string.wish_editor_name_required) else null,
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        )
        Text(
            text = stringResource(R.string.wish_editor_name_helper),
            modifier = Modifier.padding(top = GiftiarySpacing.s),
            style = GiftiaryType.metadata,
            color = GiftiaryTheme.colors.textMuted,
        )

        GiftiarySupportButton(
            text = stringResource(if (showDetails) R.string.gift_editor_fewer_details else R.string.gift_editor_more_details),
            onClick = onToggleDetails,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftiarySpacing.xl),
            leadingIcon = painterResource(if (showDetails) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down),
        )
        if (showDetails) {
            WishDetails(
                state = state,
                actions = actions,
                onChoosePhoto = onChoosePhoto,
                enabled = canEdit,
                modifier = Modifier.padding(top = GiftiarySpacing.browseGutter),
            )
        }

        GiftiaryPrimaryButton(
            text = stringResource(R.string.wish_editor_save),
            onClick = actions.onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            enabled = state.status == WishEditorStatus.EDITING && !state.isDeleting && !state.photoBusy,
            loading = state.isSaving,
            trailingArrow = true,
        )
    }
}

/** The photo first, as what a wish looks like matters most; then note, approximate price and link. */
@Composable
private fun WishDetails(
    state: WishEditorUiState,
    actions: WishEditorActions,
    onChoosePhoto: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.fieldGroup)) {
        GiftPhotoTile(
            hasPhoto = state.photoPath != null,
            photo = state.photo,
            busy = state.photoBusy,
            enabled = enabled,
            onChoose = onChoosePhoto,
            onRemove = actions.onPhotoRemoved,
            modifier = Modifier.fillMaxWidth(),
            description = stringResource(R.string.wish_photo_description),
        )
        GiftiaryTextField(
            value = state.note,
            onValueChange = actions.onNoteChange,
            label = stringResource(R.string.gift_note),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.wish_editor_note_placeholder),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = false,
            minLines = 2,
        )
        Column(verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
            GiftiaryTextField(
                value = state.price,
                onValueChange = actions.onPriceChange,
                label = stringResource(R.string.wish_editor_approx_price),
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.wish_editor_price_placeholder),
                errorMessage = state.priceError?.let { priceErrorText(it, state.currencyCode) },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                suffix = state.currencyCode,
            )
            if (state.priceError == null) {
                Text(
                    text = stringResource(R.string.wish_editor_price_helper),
                    style = GiftiaryType.metadata,
                    color = GiftiaryTheme.colors.textMuted,
                )
            }
        }
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

@PreviewLightDark
@Composable
private fun WishEditorPreview() {
    GiftiaryTheme {
        WishEditorScreen(
            state = WishEditorUiState(
                isNew = true,
                status = WishEditorStatus.EDITING,
                wishlistName = "Peter’s wishlist",
                name = "Brass lamp",
                price = "12,995",
                currencyCode = "HUF",
                priceError = PriceError.TOO_PRECISE,
            ),
            actions = WishEditorActions({}, {}, {}, {}, {}, {}, {}),
        )
    }
}
