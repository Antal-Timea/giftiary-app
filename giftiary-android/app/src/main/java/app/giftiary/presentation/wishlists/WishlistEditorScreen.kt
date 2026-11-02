package app.giftiary.presentation.wishlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Person
import app.giftiary.presentation.components.DecoratedPage
import app.giftiary.presentation.components.GiftCutout
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryDropdownField
import app.giftiary.presentation.components.GiftiaryInput
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.GiftiaryTextField
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.util.UUID

/** [onCreated] opens a newly created wishlist in place of the editor; a rename just closes. */
@Composable
fun WishlistEditorScreen(
    viewModel: WishlistEditorViewModel,
    onClose: () -> Unit,
    onCreated: (UUID) -> Unit,
    onDeleted: () -> Unit = onClose,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.status) {
        when (state.status) {
            WishlistEditorStatus.SAVED -> {
                val created = state.savedWishlistId
                if (state.isNew && created != null) onCreated(created) else onClose()
            }
            WishlistEditorStatus.DELETED -> onDeleted()
            else -> Unit
        }
    }
    WishlistEditorScreen(
        state = state,
        actions = WishlistEditorActions(
            onBack = onClose,
            onNameChange = viewModel::onNameChange,
            onPersonChange = viewModel::onPersonChange,
            onSave = viewModel::save,
            onWriteErrorShown = viewModel::onWriteErrorShown,
            onDeleteRequested = viewModel::onDeleteRequested,
            onDeleteDismissed = viewModel::onDeleteDismissed,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
        ),
    )
}

/** Groups the editor's callbacks so the screen signature stays readable. */
class WishlistEditorActions(
    val onBack: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onPersonChange: (Person?) -> Unit,
    val onSave: () -> Unit,
    val onWriteErrorShown: () -> Unit,
    // Only an existing list offers deleting.
    val onDeleteRequested: () -> Unit = {},
    val onDeleteDismissed: () -> Unit = {},
    val onDeleteConfirmed: () -> Unit = {},
)

@Composable
fun WishlistEditorScreen(state: WishlistEditorUiState, actions: WishlistEditorActions, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val writeErrorMessage = state.writeError?.let {
        stringResource(
            when (it) {
                WishlistWriteError.FAILED -> R.string.wishlist_save_failed
                WishlistWriteError.PERSON_UNAVAILABLE -> R.string.wishlist_person_unavailable
                WishlistWriteError.DELETE_FAILED -> R.string.wishlist_delete_failed
            },
        )
    }
    LaunchedEffect(writeErrorMessage) {
        if (writeErrorMessage != null) {
            snackbarHostState.showSnackbar(writeErrorMessage)
            actions.onWriteErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = GiftiaryTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state.status) {
            WishlistEditorStatus.LOADING -> GiftiaryTaskStatus(null, actions.onBack, contentModifier)
            WishlistEditorStatus.NOT_FOUND -> GiftiaryTaskStatus(stringResource(R.string.wishlist_not_found), actions.onBack, contentModifier)
            WishlistEditorStatus.LOAD_FAILED -> GiftiaryTaskStatus(stringResource(R.string.wishlist_load_failed), actions.onBack, contentModifier)
            WishlistEditorStatus.EDITING, WishlistEditorStatus.SAVED, WishlistEditorStatus.DELETED ->
                DecoratedPage(top = GiftCutout.SMALL) {
                    WishlistForm(state = state, actions = actions, modifier = contentModifier)
                }
        }
    }

    if (state.confirmingDelete) {
        val count = state.deleteWishCount
        GiftiaryConfirmDialog(
            title = stringResource(R.string.wishlist_delete_dialog_title, state.savedName),
            message = if (count == 0) {
                stringResource(R.string.wishlist_delete_dialog_message_empty)
            } else {
                pluralStringResource(R.plurals.wishlist_delete_dialog_message, count, count)
            },
            confirmLabel = stringResource(R.string.wishlist_delete_confirm),
            onConfirm = actions.onDeleteConfirmed,
            onDismiss = actions.onDeleteDismissed,
        )
    }
}

/**
 * The approved Create list: the name, who the list is for and a note on its privacy, then one
 * Save. There are no list covers, so the board's photo stays out. Who a list is for is chosen only
 * when it is created; Edit shows it but cannot change it, and leaves out the privacy note, which
 * is only certain for a new list.
 */
@Composable
private fun WishlistForm(state: WishlistEditorUiState, actions: WishlistEditorActions, modifier: Modifier = Modifier) {
    val canEdit = state.status == WishlistEditorStatus.EDITING && !state.isBusy
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .giftiaryReadableWidth()
            .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter, bottom = GiftiarySpacing.xxl),
    ) {
        GiftiaryTaskHeader(
            title = stringResource(if (state.isNew) R.string.wishlist_editor_add_title else R.string.wishlist_editor_edit_title),
            subtitle = stringResource(if (state.isNew) R.string.wishlist_editor_add_subtitle else R.string.wishlist_editor_edit_subtitle),
            onBack = actions.onBack,
            menu = if (state.isNew) null else {
                {
                    OptionsMenu(R.drawable.ic_more, stringResource(R.string.task_more)) {
                        Option(stringResource(R.string.wishlist_delete), R.drawable.ic_delete, actions.onDeleteRequested, enabled = canEdit)
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
                onValueChange = actions.onNameChange,
                label = stringResource(R.string.wishlist_editor_name),
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.wishlist_editor_name_hint),
                errorMessage = if (state.showNameRequired) stringResource(R.string.wishlist_editor_name_required) else null,
                enabled = canEdit,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (state.isNew) ImeAction.Next else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onSave() }),
            )
            Column(verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s)) {
                if (state.isNew) {
                    GiftiaryDropdownField(
                        label = stringResource(R.string.wishlist_editor_for),
                        options = state.personOptions.map(::ForChoice),
                        selected = ForChoice(state.selectedPerson),
                        optionLabel = { choice -> choice.person?.name ?: stringResource(R.string.wishlist_editor_for_me) },
                        onSelect = { actions.onPersonChange(it.person) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canEdit && state.peopleLoaded,
                        leadingIcon = state.selectedPerson?.let { person -> { GiftiaryAvatar(name = person.name, size = 26.dp) } },
                    )
                } else {
                    state.owner?.let { OwnerField(it) }
                }
                Text(
                    text = stringResource(if (state.isNew) R.string.wishlist_editor_for_hint else R.string.wishlist_editor_for_fixed),
                    style = GiftiaryType.metadata,
                    color = GiftiaryTheme.colors.textMuted,
                )
            }
            if (state.isNew) PrivacyNote(forPerson = state.selectedPerson != null)
        }
        GiftiaryPrimaryButton(
            text = stringResource(if (state.isNew) R.string.wishlist_editor_create else R.string.wishlist_editor_save),
            onClick = actions.onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftiarySpacing.xxl),
            enabled = state.status == WishlistEditorStatus.EDITING && !state.isDeleting && (!state.isNew || state.peopleLoaded),
            loading = state.isSaving,
            trailingArrow = true,
        )
    }
}

/** Who an existing list is for: shown like the field it was chosen in, but it cannot be changed. */
@Composable
private fun OwnerField(owner: WishlistOwner) {
    val name = when (owner) {
        WishlistOwner.Me -> stringResource(R.string.wishlist_editor_for_me)
        is WishlistOwner.Person -> owner.name
        WishlistOwner.PersonRemoved -> stringResource(R.string.wishlist_person_removed)
    }
    GiftiaryInput(
        value = name,
        onValueChange = {},
        label = stringResource(R.string.wishlist_editor_for),
        modifier = Modifier.fillMaxWidth(),
        readOnly = true,
        leadingIcon = (owner as? WishlistOwner.Person)?.let { { GiftiaryAvatar(name = it.name, size = 26.dp) } },
    )
}

/**
 * The board's "Only you" note. A new list is always private; only the user's own lists can be
 * shared later, so one kept for a person says it stays private instead.
 */
@Composable
private fun PrivacyNote(forPerson: Boolean) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.infoSurface, GiftiaryShapes.container)
            .padding(horizontal = 18.dp, vertical = GiftiarySpacing.l)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
            tint = colors.textPrimary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.lists_only_you),
                style = GiftiaryType.body.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(if (forPerson) R.string.wishlist_editor_private_person else R.string.wishlist_editor_private_own),
                style = GiftiaryType.body,
                color = colors.textPrimary,
            )
        }
    }
}

/** A "For" option; the dropdown treats null as nothing chosen, so the user's own list needs a value. */
private data class ForChoice(val person: Person?)

@PreviewLightDark
@Composable
private fun WishlistEditorNewPreview() {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    GiftiaryTheme {
        WishlistEditorScreen(
            state = WishlistEditorUiState(
                isNew = true,
                status = WishlistEditorStatus.EDITING,
                name = "Christmas",
                personId = anna.id,
                activePeople = listOf(anna),
                peopleLoaded = true,
            ),
            actions = WishlistEditorActions({}, {}, {}, {}, {}),
        )
    }
}

@PreviewLightDark
@Composable
private fun WishlistEditorRenamePreview() {
    GiftiaryTheme {
        WishlistEditorScreen(
            state = WishlistEditorUiState(
                isNew = false,
                status = WishlistEditorStatus.EDITING,
                name = " ",
                owner = WishlistOwner.PersonRemoved,
                showNameRequired = true,
            ),
            actions = WishlistEditorActions({}, {}, {}, {}, {}),
        )
    }
}
