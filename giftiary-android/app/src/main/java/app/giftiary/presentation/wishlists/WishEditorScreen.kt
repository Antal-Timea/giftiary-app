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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.gifts.PriceError
import app.giftiary.presentation.gifts.priceErrorText
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

@Composable
fun WishEditorScreen(
    viewModel: WishEditorViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        if (state.status == WishEditorStatus.SAVED) onClose()
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
)

@Composable
fun WishEditorScreen(state: WishEditorUiState, actions: WishEditorActions, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(R.string.wish_save_failed)
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbarHostState.showSnackbar(saveFailedMessage)
            actions.onSaveErrorShown()
        }
    }

    val title = if (state.isNew) R.string.wish_editor_add_title else R.string.wish_editor_edit_title
    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = stringResource(title), onBack = actions.onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            WishEditorStatus.LOADING -> LoadingContent(contentModifier)
            WishEditorStatus.NOT_FOUND -> MessageContent(stringResource(R.string.wish_not_found), contentModifier)
            WishEditorStatus.WISHLIST_NOT_FOUND -> MessageContent(stringResource(R.string.wishlist_not_found), contentModifier)
            WishEditorStatus.LOAD_FAILED -> MessageContent(stringResource(R.string.wish_load_failed), contentModifier)
            WishEditorStatus.EDITING, WishEditorStatus.SAVED -> WishForm(state = state, actions = actions, modifier = contentModifier)
        }
    }
}

@Composable
private fun WishForm(state: WishEditorUiState, actions: WishEditorActions, modifier: Modifier = Modifier) {
    val canEdit = state.status == WishEditorStatus.EDITING && !state.isSaving
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.l),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
    ) {
        GiftiaryTextField(
            value = state.name,
            onValueChange = actions.onNameChange,
            label = stringResource(R.string.wish_editor_name),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.wish_editor_name_hint),
            errorMessage = if (state.showNameRequired) stringResource(R.string.wish_editor_name_required) else null,
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        )
        GiftiaryTextField(
            value = state.price,
            onValueChange = actions.onPriceChange,
            label = stringResource(R.string.wish_editor_price_optional),
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
        GiftiaryPrimaryButton(
            text = stringResource(R.string.wish_editor_save),
            onClick = actions.onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftiarySpacing.s),
            enabled = state.status == WishEditorStatus.EDITING,
            loading = state.isSaving,
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
                name = "Brass lamp",
                price = "12,995",
                currencyCode = "HUF",
                priceError = PriceError.TOO_PRECISE,
            ),
            actions = WishEditorActions({}, {}, {}, {}, {}, {}, {}),
        )
    }
}
