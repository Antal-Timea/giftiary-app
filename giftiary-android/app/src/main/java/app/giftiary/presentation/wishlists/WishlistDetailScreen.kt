package app.giftiary.presentation.wishlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Wish
import app.giftiary.domain.model.Wishlist
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.gifts.giftPrice
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.util.UUID

@Composable
fun WishlistDetailScreen(
    viewModel: WishlistDetailViewModel,
    onBack: () -> Unit,
    onRename: (UUID) -> Unit,
    onOpenPerson: (UUID) -> Unit,
    onAddWish: (UUID) -> Unit,
    onOpenWish: (UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.content) {
        if (state.content == WishlistDetailContent.Deleted) onBack()
    }
    WishlistDetailScreen(
        state = state,
        actions = WishlistDetailScreenActions(
            onBack = onBack,
            onRename = onRename,
            onOpenPerson = onOpenPerson,
            onAddWish = onAddWish,
            onOpenWish = onOpenWish,
            onDeleteRequested = viewModel::onDeleteRequested,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
            onDeleteDismissed = viewModel::onDeleteDismissed,
            onWriteErrorShown = viewModel::onWriteErrorShown,
        ),
    )
}

/** Groups the screen's callbacks so its signature stays readable. */
class WishlistDetailScreenActions(
    val onBack: () -> Unit,
    val onRename: (UUID) -> Unit,
    val onOpenPerson: (UUID) -> Unit,
    val onAddWish: (UUID) -> Unit,
    val onOpenWish: (UUID) -> Unit,
    val onDeleteRequested: () -> Unit,
    val onDeleteConfirmed: () -> Unit,
    val onDeleteDismissed: () -> Unit,
    val onWriteErrorShown: () -> Unit,
)

@Composable
fun WishlistDetailScreen(
    state: WishlistDetailUiState,
    actions: WishlistDetailScreenActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val writeFailedMessage = stringResource(R.string.wishlist_delete_failed)
    LaunchedEffect(state.actions.writeFailed) {
        if (state.actions.writeFailed) {
            snackbarHostState.showSnackbar(writeFailedMessage)
            actions.onWriteErrorShown()
        }
    }

    val loaded = state.content as? WishlistDetailContent.Loaded
    Scaffold(
        modifier = modifier,
        topBar = {
            GiftiaryTopBar(
                title = loaded?.wishlist?.name.orEmpty(),
                onBack = actions.onBack,
                actions = {
                    if (loaded != null) {
                        TextButton(
                            onClick = { actions.onRename(loaded.wishlist.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.textPrimary),
                        ) {
                            Text(stringResource(R.string.wishlist_rename))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (loaded != null) AddButton(text = stringResource(R.string.wish_add), onClick = { actions.onAddWish(loaded.wishlist.id) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            WishlistDetailContent.Loading, WishlistDetailContent.Deleted -> LoadingContent(contentModifier)
            WishlistDetailContent.NotFound -> MessageContent(stringResource(R.string.wishlist_not_found), contentModifier)
            WishlistDetailContent.LoadFailed -> MessageContent(stringResource(R.string.wishlist_load_failed), contentModifier)
            is WishlistDetailContent.Loaded -> WishlistContents(
                content = content,
                busy = state.actions.isBusy,
                actions = actions,
                modifier = contentModifier,
            )
        }
    }

    if (state.actions.confirmingDelete && loaded != null) {
        val count = loaded.wishes.size
        GiftiaryConfirmDialog(
            title = stringResource(R.string.wishlist_delete_dialog_title, loaded.wishlist.name),
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

@Composable
private fun WishlistContents(
    content: WishlistDetailContent.Loaded,
    busy: Boolean,
    actions: WishlistDetailScreenActions,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = FAB_CLEARANCE)) {
        item {
            val personId = content.openablePersonId
            Text(
                text = wishlistOwnerText(content.owner),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // Only an active person can be opened; a deleted one is only named.
                        if (personId != null) {
                            Modifier.clickable(onClickLabel = stringResource(R.string.people_open)) { actions.onOpenPerson(personId) }
                        } else {
                            Modifier
                        },
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.m),
                style = MaterialTheme.typography.bodyLarge,
                color = if (personId != null) colors.primaryActionText else colors.textSecondary,
            )
        }
        item { SectionHeading(stringResource(R.string.wishlist_wishes)) }
        if (content.wishes.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.wishes_empty),
                    modifier = Modifier.padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
        }
        items(content.wishes, key = { it.id }) { wish ->
            ListRow(title = wish.name, detail = wishSummary(wish), onClick = { actions.onOpenWish(wish.id) })
            HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
        }
        item {
            // Deliberately quiet: deleting should not compete with adding wishes.
            TextButton(
                onClick = actions.onDeleteRequested,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GiftiarySpacing.l),
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
            ) {
                Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = null)
                Spacer(Modifier.width(GiftiarySpacing.s))
                Text(stringResource(R.string.wishlist_delete))
            }
        }
    }
}

/** The price and the note, whichever the wish has, on one line. */
@Composable
private fun wishSummary(wish: Wish): String? =
    listOfNotNull(wish.estimatedPrice?.let { giftPrice(it) }, wish.note)
        .joinToString(separator = " · ")
        .ifEmpty { null }

@PreviewLightDark
@Composable
private fun WishlistDetailPreview() {
    val wishlist = Wishlist(UUID.randomUUID(), null, "Birthday 2027", Instant.EPOCH, Instant.EPOCH)
    fun wish(name: String, price: Money?, note: String?) =
        Wish(UUID.randomUUID(), wishlist.id, name, note, price, null, null, Instant.EPOCH, Instant.EPOCH)
    GiftiaryTheme {
        WishlistDetailScreen(
            state = WishlistDetailUiState(
                content = WishlistDetailContent.Loaded(
                    wishlist = wishlist,
                    owner = WishlistOwner.Me,
                    openablePersonId = null,
                    wishes = listOf(wish("Brass lamp", Money(12990, "HUF"), "The small one"), wish("Wool socks", null, null)),
                ),
            ),
            actions = WishlistDetailScreenActions({}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
