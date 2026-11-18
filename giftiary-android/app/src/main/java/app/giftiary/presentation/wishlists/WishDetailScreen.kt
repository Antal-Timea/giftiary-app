package app.giftiary.presentation.wishlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Wish
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.PendingSyncStatus
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.gifts.DetailContextCard
import app.giftiary.presentation.gifts.DetailFactRow
import app.giftiary.presentation.gifts.DetailFactValue
import app.giftiary.presentation.gifts.DetailLinkValue
import app.giftiary.presentation.gifts.DetailNote
import app.giftiary.presentation.gifts.PhotoOrPlaceholder
import app.giftiary.presentation.gifts.giftPrice
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.util.UUID

@Composable
fun WishDetailScreen(
    viewModel: WishDetailViewModel,
    onBack: () -> Unit,
    onEditWish: (UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.content) {
        if (state.content == WishDetailContent.Deleted) onBack()
    }
    WishDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEditWish,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onWriteErrorShown = viewModel::onWriteErrorShown,
    )
}

@Composable
fun WishDetailScreen(
    state: WishDetailUiState,
    onBack: () -> Unit,
    onEdit: (UUID) -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onWriteErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val writeFailedMessage = stringResource(R.string.wish_delete_failed)
    LaunchedEffect(state.actions.writeFailed) {
        if (state.actions.writeFailed) {
            snackbarHostState.showSnackbar(writeFailedMessage)
            onWriteErrorShown()
        }
    }

    val loaded = state.content as? WishDetailContent.Loaded
    Scaffold(
        modifier = modifier,
        containerColor = GiftiaryTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            WishDetailContent.Loading, WishDetailContent.Deleted -> GiftiaryTaskStatus(null, onBack, contentModifier)
            WishDetailContent.NotFound -> GiftiaryTaskStatus(stringResource(R.string.wish_not_found), onBack, contentModifier)
            WishDetailContent.LoadFailed -> GiftiaryTaskStatus(stringResource(R.string.wish_load_failed), onBack, contentModifier)
            is WishDetailContent.Loaded -> WishDetails(
                content = content,
                photo = state.photo,
                waitingToSync = state.waitingToSync,
                busy = state.actions.isBusy,
                onBack = onBack,
                onEdit = { onEdit(content.wish.id) },
                onDeleteRequested = onDeleteRequested,
                modifier = contentModifier,
            )
        }
    }

    if (state.actions.confirmingDelete && loaded != null) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.wish_delete_dialog_title, loaded.wish.name),
            message = stringResource(R.string.wish_delete_dialog_message),
            confirmLabel = stringResource(R.string.wishlist_delete_confirm),
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteDismissed,
        )
    }
}

/**
 * There is no Wish detail board, so this follows the gift idea boards as far as a wish has the
 * same things: the title, the list it is on in place of the person, the photo, the facts and the
 * note. A wish has no next step of its own, so there is no primary action; editing and deleting wait
 * in the options. The photo is shown whole, as in the editor, not as a hero: most wishes have none,
 * and the page should not change shape around it. What guests reserve is never shown to the owner.
 */
@Composable
private fun WishDetails(
    content: WishDetailContent.Loaded,
    photo: WishDetailPhoto?,
    waitingToSync: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wish = content.wish
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .giftiaryReadableWidth()
            .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter, bottom = GiftiarySpacing.xxl),
    ) {
        GiftiaryTaskHeader(
            title = wish.name,
            subtitle = null,
            onBack = onBack,
            eyebrow = stringResource(R.string.wish_eyebrow).uppercase(),
            menu = {
                OptionsMenu(R.drawable.ic_more_vertical, stringResource(R.string.wish_more, wish.name)) {
                    Option(stringResource(R.string.wish_edit), R.drawable.ic_edit, onEdit, enabled = !busy)
                    Option(stringResource(R.string.wish_delete), R.drawable.ic_delete, onDeleteRequested, enabled = !busy, quiet = true)
                }
            },
        )
        // Back already leads to the list, so the card names it without leading anywhere.
        content.wishlistName?.let { name ->
            DetailContextCard(
                name = name,
                title = stringResource(R.string.wish_on_list, name),
                modifier = Modifier.padding(top = GiftiarySpacing.l),
            )
        }
        if (waitingToSync) PendingSyncStatus(modifier = Modifier.padding(top = GiftiarySpacing.m))
        // Nothing while it is read, rather than "not available" for a moment.
        if (photo != null && !photo.loading) {
            Box(modifier = Modifier.padding(top = GiftiarySpacing.l)) {
                PhotoOrPlaceholder(photo.photo, stringResource(R.string.wish_photo_description))
            }
        }
        Column(modifier = Modifier.padding(top = GiftiarySpacing.s).fillMaxWidth()) {
            wish.estimatedPrice?.let {
                DetailFactRow(stringResource(R.string.wish_editor_approx_price)) {
                    DetailFactValue(stringResource(R.string.wish_price_estimate, giftPrice(it)))
                }
            }
            wish.productUrl?.let { url ->
                if (wish.estimatedPrice != null) GiftiaryDivider()
                DetailFactRow(stringResource(R.string.gift_product_url)) { DetailLinkValue(url) }
            }
        }
        wish.note?.takeIf { it.isNotBlank() }?.let { DetailNote(it) }
    }
}

@PreviewLightDark
@Composable
private fun WishDetailPreview() {
    val wish = Wish(
        id = UUID.randomUUID(),
        wishlistId = UUID.randomUUID(),
        name = "Brass lamp",
        note = "The small one, for the desk.",
        estimatedPrice = Money(12990, "HUF"),
        productUrl = "https://example.com/lamp",
        photoPath = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
    GiftiaryTheme {
        WishDetailScreen(
            state = WishDetailUiState(content = WishDetailContent.Loaded(wish, "Birthday 2027")),
            onBack = {},
            onEdit = {},
            onDeleteRequested = {},
            onDeleteConfirmed = {},
            onDeleteDismissed = {},
            onWriteErrorShown = {},
        )
    }
}
