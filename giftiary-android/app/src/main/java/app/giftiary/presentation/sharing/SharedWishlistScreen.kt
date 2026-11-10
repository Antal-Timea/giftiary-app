package app.giftiary.presentation.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.ReservationState
import app.giftiary.domain.model.SharedWish
import app.giftiary.domain.model.SharedWishlist
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryBackButton
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import app.giftiary.presentation.wishlists.WishDivider
import app.giftiary.presentation.wishlists.WishRowContent
import app.giftiary.presentation.wishlists.WishlistHeader
import app.giftiary.presentation.wishlists.trimVertical
import java.util.UUID

@Composable
fun SharedWishlistScreen(viewModel: SharedWishlistViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leaving by viewModel.leaving.collectAsStateWithLifecycle()
    val reservations by viewModel.reservations.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(leaving.left) {
        if (leaving.left) onBack()
    }
    SharedWishlistScreen(
        state = state,
        leaving = leaving,
        reservations = reservations,
        actions = SharedWishlistActions(
            onBack = onBack,
            onRetry = viewModel::refresh,
            onRefreshFailureShown = viewModel::onRefreshFailureShown,
            onLeaveRequested = viewModel::onLeaveRequested,
            onLeaveConfirmed = viewModel::onLeaveConfirmed,
            onLeaveDismissed = viewModel::onLeaveDismissed,
            onLeaveFailureShown = viewModel::onLeaveFailureShown,
            onReserve = viewModel::onReserve,
            onReleaseRequested = viewModel::onReleaseRequested,
            onReleaseConfirmed = viewModel::onReleaseConfirmed,
            onReleaseDismissed = viewModel::onReleaseDismissed,
            onReservationMessageShown = viewModel::onReservationMessageShown,
        ),
    )
}

class SharedWishlistActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRefreshFailureShown: () -> Unit,
    val onLeaveRequested: () -> Unit,
    val onLeaveConfirmed: () -> Unit,
    val onLeaveDismissed: () -> Unit,
    val onLeaveFailureShown: () -> Unit,
    val onReserve: (UUID) -> Unit,
    val onReleaseRequested: (UUID) -> Unit,
    val onReleaseConfirmed: () -> Unit,
    val onReleaseDismissed: () -> Unit,
    val onReservationMessageShown: () -> Unit,
)

/** The wishes as the owner keeps them; the guest can reserve, release and leave. */
@Composable
fun SharedWishlistScreen(
    state: OnlineState<SharedWishlist>,
    leaving: LeaveState,
    reservations: ReservationActions,
    actions: SharedWishlistActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailure = state.refreshFailure?.let { retryableFailureText(it) }
    LaunchedEffect(state.refreshFailure) {
        if (refreshFailure != null) {
            snackbarHostState.showSnackbar(refreshFailure)
            actions.onRefreshFailureShown()
        }
    }
    val leaveFailure = leaving.failure?.let { retryableFailureText(it) }
    LaunchedEffect(leaving.failure) {
        if (leaveFailure != null) {
            snackbarHostState.showSnackbar(leaveFailure)
            actions.onLeaveFailureShown()
        }
    }
    val reservationMessage = reservations.message?.let { reservationMessageText(it) }
    LaunchedEffect(reservations.message) {
        if (reservationMessage != null) {
            snackbarHostState.showSnackbar(reservationMessage)
            actions.onReservationMessageShown()
        }
    }
    val loaded = (state.content as? OnlineContent.Loaded)?.value
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier
            .padding(contentPadding)
            .windowInsetsPadding(WindowInsets.statusBars)
        when (val content = state.content) {
            OnlineContent.Loading -> Column(contentModifier.fillMaxSize()) {
                BackOnly(actions.onBack)
                LoadingContent()
            }
            is OnlineContent.Failed -> Column(contentModifier.fillMaxSize()) {
                BackOnly(actions.onBack)
                if (content.failure == SharingFailure.UNAVAILABLE) {
                    // The same whether the list was deleted, access was withdrawn or it never existed.
                    FailureContent(stringResource(R.string.shared_wishlist_unavailable), onRetry = null)
                } else {
                    FailureContent(retryableFailureText(content.failure), actions.onRetry)
                }
            }
            is OnlineContent.Loaded -> SharedWishes(
                wishlist = content.value,
                isRefreshing = state.isRefreshing,
                isLeaving = leaving.isLeaving,
                inFlight = reservations.inFlight,
                actions = actions,
                modifier = contentModifier,
            )
        }
    }

    if (leaving.confirming && loaded != null) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.shared_wishlist_leave_dialog_title, loaded.name),
            message = stringResource(R.string.shared_wishlist_leave_dialog_message),
            confirmLabel = stringResource(R.string.shared_wishlist_leave_confirm),
            onConfirm = actions.onLeaveConfirmed,
            onDismiss = actions.onLeaveDismissed,
        )
    }

    val releasing = reservations.confirmingRelease?.let { id -> loaded?.wishes?.find { it.id == id } }
    if (releasing != null) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.shared_wish_release_dialog_title, releasing.name),
            message = stringResource(R.string.shared_wish_release_dialog_message),
            confirmLabel = stringResource(R.string.shared_wish_release),
            onConfirm = actions.onReleaseConfirmed,
            onDismiss = actions.onReleaseDismissed,
        )
    }
}

/** Before the list is known there is no header yet, but back is always there. */
@Composable
private fun BackOnly(onBack: () -> Unit) {
    GiftiaryBackButton(
        onClick = onBack,
        modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, top = GiftiarySpacing.fieldGroup),
    )
}

/** No owner controls: a guest sees the wishes and, for each, only their own reservation choice. */
@Composable
private fun SharedWishes(
    wishlist: SharedWishlist,
    isRefreshing: Boolean,
    isLeaving: Boolean,
    inFlight: Set<UUID>,
    actions: SharedWishlistActions,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    val whose = sharedListWhose(wishlist.personName, wishlist.ownerDisplayName)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .giftiaryReadableWidth(),
        contentPadding = PaddingValues(bottom = GiftiarySpacing.xxl),
    ) {
        item {
            WishlistHeader(
                title = wishlist.name,
                meta = listOf(whose, wishCount(wishlist.wishes.size)).joinToString(" · "),
                onBack = actions.onBack,
                leading = { GiftiaryAvatar(name = wishlist.personName ?: wishlist.ownerDisplayName, size = 56.dp) },
                options = {
                    OptionsMenu(R.drawable.ic_more, stringResource(R.string.wishlist_more, wishlist.name)) {
                        Option(stringResource(R.string.shared_wishlist_leave), R.drawable.ic_close, actions.onLeaveRequested, enabled = !isLeaving, quiet = true, alone = true)
                    }
                },
            )
        }
        if (isRefreshing) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.s),
                    color = colors.primaryAction,
                    trackColor = colors.divider,
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.wishlist_wishes),
                modifier = Modifier
                    .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.fieldGroup)
                    .semantics { heading() },
                style = GiftiaryType.sectionTitle,
                color = colors.textPrimary,
            )
        }
        if (wishlist.wishes.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.shared_wishlist_no_wishes),
                    modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.s),
                    style = GiftiaryType.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        itemsIndexed(wishlist.wishes, key = { _, wish -> wish.id }) { index, wish ->
            if (index > 0) WishDivider()
            WishRowContent(
                name = wish.name,
                note = wish.note,
                price = wish.estimatedPrice,
                productUrl = wish.productUrl,
                below = {
                    Reservation(
                        wishName = wish.name,
                        presentation = reservationPresentation(wish.reservation),
                        busy = wish.id in inFlight,
                        onReserve = { actions.onReserve(wish.id) },
                        onRelease = { actions.onReleaseRequested(wish.id) },
                    )
                },
            )
        }
    }
}

@Composable
private fun wishCount(count: Int): String =
    if (count == 0) {
        stringResource(R.string.person_detail_wishlist_no_wishes)
    } else {
        pluralStringResource(R.plurals.person_detail_wish_count, count, count)
    }

/**
 * The reservation in words, never colour alone, and the one action it allows. While the server
 * decides, the last state it reported stays and its action waits. Someone else's reservation is
 * only "Reserved": no action and nothing about who.
 */
@Composable
private fun Reservation(
    wishName: String,
    presentation: ReservationPresentation,
    busy: Boolean,
    onReserve: () -> Unit,
    onRelease: () -> Unit,
) {
    val colors = GiftiaryTheme.colors
    when (presentation.label ?: return) {
        ReservationLabel.AVAILABLE -> Row(verticalAlignment = Alignment.CenterVertically) {
            val description = stringResource(R.string.shared_wish_get_description, wishName)
            val available = stringResource(R.string.shared_wish_available)
            TextButton(
                onClick = onReserve,
                enabled = !busy,
                modifier = Modifier
                    .trimVertical(7.dp)
                    .heightIn(min = 48.dp)
                    .offset(x = -GiftiarySpacing.s)
                    .semantics {
                        contentDescription = description
                        stateDescription = available
                    },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryAction),
                contentPadding = PaddingValues(horizontal = GiftiarySpacing.s),
            ) {
                Icon(painterResource(R.drawable.ic_gift_reserve), contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.shared_wish_get), style = GiftiaryType.actionSmall)
            }
            if (busy) ReservationProgress()
        }
        // At large text Undo moves under the tag instead of being squeezed beside it.
        ReservationLabel.RESERVED_BY_YOU -> FlowRow(
            modifier = Modifier.padding(top = GiftiarySpacing.s),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .background(colors.supportSurface, GiftiaryShapes.pill)
                    .padding(horizontal = 14.dp, vertical = GiftiarySpacing.s),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.textPrimary)
                Text(stringResource(R.string.shared_wish_getting), style = GiftiaryType.label, color = colors.textPrimary)
            }
            if (presentation.action == ReservationAction.RELEASE) {
                val description = stringResource(R.string.shared_wish_undo_description, wishName)
                TextButton(
                    onClick = onRelease,
                    enabled = !busy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = description },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textLabel),
                    contentPadding = PaddingValues(horizontal = GiftiarySpacing.xs),
                ) {
                    Text(stringResource(R.string.shared_wish_undo), style = GiftiaryType.label.copy(fontWeight = GiftiaryType.contentTitle.fontWeight))
                }
            }
            if (busy) ReservationProgress()
        }
        ReservationLabel.RESERVED -> ReservedTag()
    }
}

/** Someone reserved the wish; never who. Guests and the owner of a list kept for a person see it. */
@Composable
internal fun ReservedTag() {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = Modifier.padding(top = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_reserved), contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.textLabel)
        Text(stringResource(R.string.shared_wish_reserved), style = GiftiaryType.label, color = colors.textLabel)
    }
}

@Composable
private fun ReservationProgress() {
    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = GiftiaryTheme.colors.primaryAction, strokeWidth = 2.dp)
}

@Composable
private fun reservationMessageText(message: ReservationMessage): String = when (message) {
    ReservationMessage.TAKEN_BY_OTHER -> stringResource(R.string.shared_wish_taken)
    ReservationMessage.WISH_UNAVAILABLE -> stringResource(R.string.shared_wish_unavailable)
    ReservationMessage.NETWORK -> stringResource(R.string.sharing_network_error)
    ReservationMessage.SERVER -> stringResource(R.string.sharing_server_error)
}

@PreviewLightDark
@Composable
private fun SharedWishlistPreview() {
    GiftiaryTheme {
        SharedWishlistScreen(
            state = OnlineState(
                OnlineContent.Loaded(
                    SharedWishlist(
                        id = UUID.randomUUID(),
                        name = "Anna’s birthday",
                        ownerDisplayName = null,
                        wishes = listOf(
                            SharedWish(UUID.randomUUID(), "Brass lamp", "The small one", Money(12990, "HUF"), "https://example.com/lamp", ReservationState.AVAILABLE),
                            SharedWish(UUID.randomUUID(), "Wool socks", null, null, null, ReservationState.RESERVED_BY_ME),
                            SharedWish(UUID.randomUUID(), "Tea set", null, null, null, ReservationState.RESERVED_BY_OTHER),
                        ),
                    ),
                ),
            ),
            leaving = LeaveState(),
            reservations = ReservationActions(),
            actions = SharedWishlistActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
