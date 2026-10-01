package app.gifty.presentation.giftideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import app.gifty.R
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.Occasion
import app.gifty.domain.model.Person
import app.gifty.domain.usecase.GiftIdeaInputError
import app.gifty.presentation.components.GiftyDropdownField
import app.gifty.presentation.components.GiftyPrimaryButton
import app.gifty.presentation.components.GiftyTextField
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.gifts.priceErrorText
import app.gifty.presentation.occasions.occasionTitle
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme
import java.time.Instant
import java.util.UUID

@Composable
fun GiftIdeaEditorScreen(
    viewModel: GiftIdeaEditorViewModel,
    onClose: () -> Unit,
    onOpenPeople: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        if (state.status == GiftIdeaEditorStatus.CLOSED) onClose()
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
            },
        )
    }
    LaunchedEffect(writeErrorMessage) {
        if (writeErrorMessage != null) {
            snackbarHostState.showSnackbar(writeErrorMessage)
            actions.onWriteErrorShown()
        }
    }

    val title = if (state.isNew) R.string.gift_idea_editor_add_title else R.string.gift_idea_editor_edit_title
    Scaffold(
        modifier = modifier,
        topBar = { GiftyTopBar(title = stringResource(title), onBack = actions.onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            GiftIdeaEditorStatus.LOADING -> LoadingContent(contentModifier)
            GiftIdeaEditorStatus.NOT_FOUND -> MessageContent(stringResource(R.string.gift_idea_not_found), contentModifier)
            GiftIdeaEditorStatus.LOAD_FAILED -> MessageContent(stringResource(R.string.gift_idea_load_failed), contentModifier)
            GiftIdeaEditorStatus.EDITING, GiftIdeaEditorStatus.CLOSED ->
                GiftIdeaForm(state = state, actions = actions, modifier = contentModifier)
        }
    }
}

@Composable
private fun GiftIdeaForm(state: GiftIdeaEditorUiState, actions: GiftIdeaEditorActions, modifier: Modifier = Modifier) {
    val canEdit = state.status == GiftIdeaEditorStatus.EDITING && !state.isBusy
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = GiftySpacing.xl, vertical = GiftySpacing.l),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.l),
    ) {
        if (state.needsPerson) {
            Text(
                text = stringResource(R.string.gift_idea_editor_needs_person),
                style = MaterialTheme.typography.bodyLarge,
                color = GiftyTheme.colors.textSecondary,
            )
            GiftyPrimaryButton(
                text = stringResource(R.string.gift_editor_go_to_people),
                onClick = actions.onOpenPeople,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        GiftyDropdownField(
            label = stringResource(R.string.gift_recipient),
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
        )
        GiftyTextField(
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
        GiftIdeaStatusSelector(selected = state.ideaStatus, onSelect = actions.onStatusChange, enabled = canEdit)
        GiftyDropdownField(
            label = stringResource(R.string.gift_occasion),
            options = state.occasionOptions,
            selected = state.selectedOccasion,
            optionLabel = { occasion -> occasion?.let { occasionTitle(it) } ?: stringResource(R.string.gift_no_occasion) },
            onSelect = actions.onOccasionChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit && state.recipientId != null,
        )
        GiftyTextField(
            value = state.estimatedPrice,
            onValueChange = actions.onEstimatedPriceChange,
            label = stringResource(R.string.gift_idea_estimated_price_optional),
            modifier = Modifier.fillMaxWidth(),
            errorMessage = state.estimatedPriceError?.let { priceErrorText(it, state.estimatedCurrencyCode) },
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            suffix = state.estimatedCurrencyCode,
        )
        if (state.showsActualPrice) {
            GiftyTextField(
                value = state.actualPrice,
                onValueChange = actions.onActualPriceChange,
                label = stringResource(R.string.gift_idea_actual_price_optional),
                modifier = Modifier.fillMaxWidth(),
                errorMessage = state.actualPriceError?.let { priceErrorText(it, state.actualCurrencyCode) },
                enabled = canEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                suffix = state.actualCurrencyCode,
            )
        }
        GiftyTextField(
            value = state.productUrl,
            onValueChange = actions.onProductUrlChange,
            label = stringResource(R.string.gift_product_url_optional),
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        GiftyTextField(
            value = state.note,
            onValueChange = actions.onNoteChange,
            label = stringResource(R.string.gift_note_optional),
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = false,
            minLines = 3,
        )
        GiftyPrimaryButton(
            text = stringResource(R.string.gift_idea_editor_save),
            onClick = actions.onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftySpacing.s),
            enabled = state.status == GiftIdeaEditorStatus.EDITING,
            loading = state.isBusy,
        )
    }
}

private val previewActions = GiftIdeaEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@PreviewLightDark
@Composable
private fun GiftIdeaEditorPreview() {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    GiftyTheme {
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
