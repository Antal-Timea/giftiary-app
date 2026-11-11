package app.giftiary.presentation.wishlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Wish
import app.giftiary.domain.model.Wishlist
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.PendingSyncStatus
import app.giftiary.presentation.components.TrailingActionRow
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.sharing.ReservedTag
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.util.UUID

@Composable
fun WishlistDetailScreen(
    viewModel: WishlistDetailViewModel,
    onBack: () -> Unit,
    onEditList: (UUID) -> Unit,
    onOpenPerson: (UUID) -> Unit,
    onAddWish: (UUID) -> Unit,
    onOpenWish: (UUID) -> Unit,
    onEditWish: (UUID) -> Unit,
    onShare: (UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Reservations change elsewhere; ask again whenever the list comes back into view.
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.content) {
        if (state.content == WishlistDetailContent.Deleted) onBack()
    }
    WishlistDetailScreen(
        state = state,
        actions = WishlistDetailScreenActions(
            onBack = onBack,
            onEditList = onEditList,
            onOpenPerson = onOpenPerson,
            onAddWish = onAddWish,
            onOpenWish = onOpenWish,
            onEditWish = onEditWish,
            onShare = onShare,
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
    val onEditList: (UUID) -> Unit,
    val onOpenPerson: (UUID) -> Unit,
    val onAddWish: (UUID) -> Unit,
    val onOpenWish: (UUID) -> Unit,
    val onEditWish: (UUID) -> Unit,
    val onShare: (UUID) -> Unit,
    val onDeleteRequested: () -> Unit,
    val onDeleteConfirmed: () -> Unit,
    val onDeleteDismissed: () -> Unit,
    val onWriteErrorShown: () -> Unit,
)

/** The owner's own view of a list: its identity, sharing, and the wishes with their actions. */
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
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier
            .padding(contentPadding)
            .windowInsetsPadding(WindowInsets.statusBars)
        when (val content = state.content) {
            WishlistDetailContent.Loading, WishlistDetailContent.Deleted -> LoadingContent(contentModifier)
            WishlistDetailContent.NotFound -> MessageContent(stringResource(R.string.wishlist_not_found), contentModifier)
            WishlistDetailContent.LoadFailed -> MessageContent(stringResource(R.string.wishlist_load_failed), contentModifier)
            is WishlistDetailContent.Loaded -> WishlistContents(
                content = content,
                waitingToSync = state.waitingToSync,
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
    waitingToSync: Boolean,
    busy: Boolean,
    actions: WishlistDetailScreenActions,
    modifier: Modifier = Modifier,
) {
    val wishlist = content.wishlist
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .giftiaryReadableWidth(),
        contentPadding = PaddingValues(bottom = GiftiarySpacing.xxl),
    ) {
        item {
            val personId = content.openablePersonId
            val owner = content.owner
            WishlistHeader(
                title = wishlist.name,
                meta = listOf(ownerName(owner), wishCount(content.wishes.size)).joinToString(" · "),
                onBack = actions.onBack,
                leading = {
                    if (owner is WishlistOwner.Person) GiftiaryAvatar(name = owner.name, size = 56.dp) else ListCover(wishlist.name, size = 56.dp)
                },
                // Only an active person can be opened; a deleted one is only named.
                onMetaClick = personId?.let { { actions.onOpenPerson(it) } },
                metaClickLabel = stringResource(R.string.people_open),
                options = {
                    OptionsMenu(R.drawable.ic_more, stringResource(R.string.wishlist_more, wishlist.name)) {
                        Option(stringResource(R.string.wishlist_edit), R.drawable.ic_edit, { actions.onEditList(wishlist.id) })
                        Option(stringResource(R.string.wishlist_delete), R.drawable.ic_delete, actions.onDeleteRequested, enabled = !busy, quiet = true)
                    }
                },
                below = { SharingAction(onClick = { actions.onShare(wishlist.id) }) },
            )
        }
        if (waitingToSync) {
            item { PendingSyncStatus(modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, top = GiftiarySpacing.l)) }
        }
        item { WishesHeading(onAddWish = { actions.onAddWish(wishlist.id) }) }
        if (content.wishes.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.wishes_empty),
                    modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.s),
                    style = GiftiaryType.bodySmall,
                    color = GiftiaryTheme.colors.textSecondary,
                )
            }
        }
        itemsIndexed(content.wishes, key = { _, wish -> wish.id }) { index, wish ->
            if (index > 0) WishDivider()
            OwnerWishRow(
                wish = wish,
                reserved = wish.id in content.reservedWishIds,
                onOpen = { actions.onOpenWish(wish.id) },
                onEdit = { actions.onEditWish(wish.id) },
            )
        }
    }
}

@Composable
private fun ownerName(owner: WishlistOwner): String = when (owner) {
    is WishlistOwner.Person -> owner.name
    else -> wishlistOwnerText(owner)
}

@Composable
private fun wishCount(count: Int): String =
    if (count == 0) {
        stringResource(R.string.person_detail_wishlist_no_wishes)
    } else {
        pluralStringResource(R.plurals.person_detail_wish_count, count, count)
    }

/**
 * The list's one way into sharing. Who has joined is only known online, on the sharing screen, so
 * the header names the action rather than a list of people it could get wrong offline.
 */
@Composable
private fun SharingAction(onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .trimVertical(12.dp)
            .heightIn(min = 48.dp)
            .offset(x = -GiftiarySpacing.s),
        colors = ButtonDefaults.textButtonColors(contentColor = colors.textPrimary),
        contentPadding = PaddingValues(horizontal = GiftiarySpacing.s),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(colors.supportSurface, GiftiaryShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_people), contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.textPrimary)
        }
        Spacer(Modifier.width(GiftiarySpacing.s))
        Text(stringResource(R.string.wishlist_sharing), style = GiftiaryType.label)
    }
}

@Composable
private fun WishesHeading(onAddWish: () -> Unit) {
    val colors = GiftiaryTheme.colors
    val addDescription = stringResource(R.string.wish_add_description)
    TrailingActionRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.m, top = GiftiarySpacing.fieldGroup),
        action = {
            TextButton(
                onClick = onAddWish,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = addDescription },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryAction),
                contentPadding = PaddingValues(horizontal = GiftiarySpacing.s),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.wish_add), style = GiftiaryType.label.copy(fontWeight = GiftiaryType.contentTitle.fontWeight))
            }
        },
    ) {
        Text(
            text = stringResource(R.string.wishlist_wishes),
            modifier = Modifier.semantics { heading() },
            style = GiftiaryType.sectionTitle,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun OwnerWishRow(wish: Wish, reserved: Boolean, onOpen: () -> Unit, onEdit: () -> Unit) {
    WishRowContent(
        name = wish.name,
        note = wish.note,
        price = wish.estimatedPrice,
        productUrl = wish.productUrl,
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.wish_open), onClick = onOpen),
        trailing = {
            OptionsMenu(
                R.drawable.ic_more_vertical,
                stringResource(R.string.wish_more, wish.name),
                modifier = Modifier.offset(x = 14.dp, y = (-11).dp),
            ) {
                Option(stringResource(R.string.wish_edit), R.drawable.ic_edit, onEdit)
            }
        },
        below = { if (reserved) ReservedTag() },
    )
}

@Composable
internal fun WishDivider() {
    GiftiaryDivider(
        modifier = Modifier.padding(end = GiftiarySpacing.browseGutter),
        startIndent = GiftiarySpacing.browseGutter + WISH_THUMBNAIL + 14.dp,
    )
}

@PreviewLightDark
@Composable
private fun WishlistDetailPreview() {
    val wishlist = Wishlist(UUID.randomUUID(), null, "Birthday 2027", Instant.EPOCH, Instant.EPOCH)
    fun wish(name: String, price: Money?, note: String?) =
        Wish(UUID.randomUUID(), wishlist.id, name, note, price, "https://example.com", null, Instant.EPOCH, Instant.EPOCH)
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
            actions = WishlistDetailScreenActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
