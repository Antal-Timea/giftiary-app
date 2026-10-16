package app.gifty.presentation.wishlists

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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.model.Person
import app.gifty.presentation.components.GiftyDropdownField
import app.gifty.presentation.components.GiftyPrimaryButton
import app.gifty.presentation.components.GiftyTextField
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.gifts.DetailField
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme
import java.time.Instant
import java.util.UUID

/** [onCreated] opens a newly created wishlist in place of the editor; a rename just closes. */
@Composable
fun WishlistEditorScreen(
    viewModel: WishlistEditorViewModel,
    onClose: () -> Unit,
    onCreated: (UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        if (state.status == WishlistEditorStatus.SAVED) {
            val created = state.savedWishlistId
            if (state.isNew && created != null) onCreated(created) else onClose()
        }
    }
    WishlistEditorScreen(
        state = state,
        onBack = onClose,
        onNameChange = viewModel::onNameChange,
        onPersonChange = viewModel::onPersonChange,
        onSave = viewModel::save,
        onWriteErrorShown = viewModel::onWriteErrorShown,
    )
}

@Composable
fun WishlistEditorScreen(
    state: WishlistEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onPersonChange: (Person?) -> Unit,
    onSave: () -> Unit,
    onWriteErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val writeErrorMessage = state.writeError?.let {
        stringResource(
            when (it) {
                WishlistWriteError.FAILED -> R.string.wishlist_save_failed
                WishlistWriteError.PERSON_UNAVAILABLE -> R.string.wishlist_person_unavailable
            },
        )
    }
    LaunchedEffect(writeErrorMessage) {
        if (writeErrorMessage != null) {
            snackbarHostState.showSnackbar(writeErrorMessage)
            onWriteErrorShown()
        }
    }

    val title = if (state.isNew) R.string.wishlist_editor_add_title else R.string.wishlist_editor_rename_title
    Scaffold(
        modifier = modifier,
        topBar = { GiftyTopBar(title = stringResource(title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            WishlistEditorStatus.LOADING -> LoadingContent(contentModifier)
            WishlistEditorStatus.NOT_FOUND -> MessageContent(stringResource(R.string.wishlist_not_found), contentModifier)
            WishlistEditorStatus.LOAD_FAILED -> MessageContent(stringResource(R.string.wishlist_load_failed), contentModifier)
            WishlistEditorStatus.EDITING, WishlistEditorStatus.SAVED -> WishlistForm(
                state = state,
                onNameChange = onNameChange,
                onPersonChange = onPersonChange,
                onSave = onSave,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun WishlistForm(
    state: WishlistEditorUiState,
    onNameChange: (String) -> Unit,
    onPersonChange: (Person?) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canEdit = state.status == WishlistEditorStatus.EDITING && !state.isSaving
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = GiftySpacing.xl, vertical = GiftySpacing.l),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.l),
    ) {
        GiftyTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = stringResource(R.string.wishlist_editor_name),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.wishlist_editor_name_hint),
            errorMessage = if (state.showNameRequired) stringResource(R.string.wishlist_editor_name_required) else null,
            enabled = canEdit,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (state.isNew) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
        )
        if (state.isNew) {
            GiftyDropdownField(
                label = stringResource(R.string.wishlist_editor_for),
                options = state.personOptions.map(::ForChoice),
                selected = ForChoice(state.selectedPerson),
                optionLabel = { choice -> choice.person?.name ?: stringResource(R.string.wishlist_editor_for_me) },
                onSelect = { onPersonChange(it.person) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canEdit && state.peopleLoaded,
            )
            Text(
                text = stringResource(R.string.wishlist_editor_for_fixed_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = GiftyTheme.colors.textSecondary,
            )
        } else {
            state.owner?.let { DetailField(label = stringResource(R.string.wishlist_editor_for), value = wishlistOwnerText(it)) }
        }
        GiftyPrimaryButton(
            text = stringResource(R.string.wishlist_editor_save),
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftySpacing.s),
            enabled = state.status == WishlistEditorStatus.EDITING && (!state.isNew || state.peopleLoaded),
            loading = state.isSaving,
        )
    }
}

/** A "For" option; the dropdown treats null as nothing chosen, so the user's own list needs a value. */
private data class ForChoice(val person: Person?)

@PreviewLightDark
@Composable
private fun WishlistEditorNewPreview() {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    GiftyTheme {
        WishlistEditorScreen(
            state = WishlistEditorUiState(
                isNew = true,
                status = WishlistEditorStatus.EDITING,
                name = "Christmas",
                personId = anna.id,
                activePeople = listOf(anna),
                peopleLoaded = true,
            ),
            onBack = {},
            onNameChange = {},
            onPersonChange = {},
            onSave = {},
            onWriteErrorShown = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun WishlistEditorRenamePreview() {
    GiftyTheme {
        WishlistEditorScreen(
            state = WishlistEditorUiState(
                isNew = false,
                status = WishlistEditorStatus.EDITING,
                name = " ",
                owner = WishlistOwner.PersonRemoved,
                showNameRequired = true,
            ),
            onBack = {},
            onNameChange = {},
            onPersonChange = {},
            onSave = {},
            onWriteErrorShown = {},
        )
    }
}
