package app.gifty.presentation.wishlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.model.Money
import app.gifty.domain.model.Wish
import app.gifty.presentation.components.GiftyConfirmDialog
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.gifts.DetailField
import app.gifty.presentation.gifts.ProductLink
import app.gifty.presentation.gifts.giftPrice
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme
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
        topBar = {
            GiftyTopBar(
                title = loaded?.wish?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (loaded != null) {
                        TextButton(
                            onClick = { onEdit(loaded.wish.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.textPrimary),
                        ) {
                            Text(stringResource(R.string.gift_edit))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            WishDetailContent.Loading, WishDetailContent.Deleted -> LoadingContent(contentModifier)
            WishDetailContent.NotFound -> MessageContent(stringResource(R.string.wish_not_found), contentModifier)
            WishDetailContent.LoadFailed -> MessageContent(stringResource(R.string.wish_load_failed), contentModifier)
            is WishDetailContent.Loaded -> WishDetails(
                content = content,
                busy = state.actions.isBusy,
                onDeleteRequested = onDeleteRequested,
                modifier = contentModifier,
            )
        }
    }

    if (state.actions.confirmingDelete && loaded != null) {
        GiftyConfirmDialog(
            title = stringResource(R.string.wish_delete_dialog_title, loaded.wish.name),
            message = stringResource(R.string.wish_delete_dialog_message),
            confirmLabel = stringResource(R.string.wishlist_delete_confirm),
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteDismissed,
        )
    }
}

@Composable
private fun WishDetails(
    content: WishDetailContent.Loaded,
    busy: Boolean,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wish = content.wish
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GiftySpacing.l, vertical = GiftySpacing.s),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.l),
    ) {
        content.wishlistName?.let { DetailField(label = stringResource(R.string.wish_on_wishlist), value = it) }
        wish.estimatedPrice?.let { DetailField(label = stringResource(R.string.wish_editor_price), value = giftPrice(it)) }
        wish.productUrl?.let { ProductLink(it) }
        wish.note?.let { DetailField(label = stringResource(R.string.gift_note), value = it) }
        // Deliberately quiet, as on the other detail screens.
        TextButton(
            onClick = onDeleteRequested,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.textSecondary),
        ) {
            Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = null)
            Spacer(Modifier.width(GiftySpacing.s))
            Text(stringResource(R.string.wish_delete))
        }
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
    GiftyTheme {
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
