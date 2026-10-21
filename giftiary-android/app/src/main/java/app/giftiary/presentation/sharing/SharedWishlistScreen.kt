package app.giftiary.presentation.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.SectionHeading
import app.giftiary.presentation.gifts.ProductLink
import app.giftiary.presentation.gifts.giftPrice
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
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
    val onBack = actions.onBack
    val onRetry = actions.onRetry
    val loaded = (state.content as? OnlineContent.Loaded)?.value
    Scaffold(
        modifier = modifier,
        topBar = { GiftiaryTopBar(title = loaded?.name.orEmpty(), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            OnlineContent.Loading -> LoadingContent(contentModifier)
            is OnlineContent.Failed -> if (content.failure == SharingFailure.UNAVAILABLE) {
                // The same whether the list was deleted, access was withdrawn or it never existed.
                FailureContent(stringResource(R.string.shared_wishlist_unavailable), onRetry = null, modifier = contentModifier)
            } else {
                FailureContent(retryableFailureText(content.failure), onRetry, contentModifier)
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
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = GiftiarySpacing.xxl)) {
        item {
            if (isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.primaryAction)
            Text(
                text = sharedWishlistOwnerText(wishlist.ownerDisplayName),
                modifier = Modifier.padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.m),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
        }
        item { SectionHeading(stringResource(R.string.wishlist_wishes)) }
        if (wishlist.wishes.isEmpty()) item { ListNote(stringResource(R.string.shared_wishlist_no_wishes)) }
        items(wishlist.wishes, key = { it.id }) { wish ->
            SharedWishRow(
                wish = wish,
                busy = wish.id in inFlight,
                onReserve = { actions.onReserve(wish.id) },
                onRelease = { actions.onReleaseRequested(wish.id) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
        }
        item {
            // Quiet, as deleting is on the user's own lists.
            TextButton(
                onClick = actions.onLeaveRequested,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GiftiarySpacing.l),
                enabled = !isLeaving,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
            ) {
                Text(stringResource(R.string.shared_wishlist_leave))
            }
        }
    }
}

@Composable
private fun SharedWishRow(wish: SharedWish, busy: Boolean, onReserve: () -> Unit, onRelease: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
    ) {
        Text(text = wish.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        val summary = listOfNotNull(wish.estimatedPrice?.let { giftPrice(it) }, wish.note).joinToString(separator = " · ")
        if (summary.isNotEmpty()) {
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        // Someone else's link: only web links are offered for opening.
        wish.productUrl?.takeIf { it.isWebLink() }?.let { ProductLink(it) }
        ReservationLine(wish.name, reservationPresentation(wish.reservation), busy, onReserve, onRelease)
    }
}

/**
 * The reservation as text, never colour alone, and the one action it allows. While the server
 * decides, the last state it reported stays and the action waits.
 */
@Composable
private fun ReservationLine(
    wishName: String,
    presentation: ReservationPresentation,
    busy: Boolean,
    onReserve: () -> Unit,
    onRelease: () -> Unit,
) {
    val label = presentation.label ?: return
    val colors = GiftiaryTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                when (label) {
                    ReservationLabel.AVAILABLE -> R.string.shared_wish_available
                    ReservationLabel.RESERVED_BY_YOU -> R.string.shared_wish_reserved_by_you
                    ReservationLabel.RESERVED -> R.string.shared_wish_reserved
                },
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = if (label == ReservationLabel.RESERVED_BY_YOU) colors.primaryActionText else colors.textSecondary,
        )
        val action = presentation.action ?: return@Row
        val reserve = action == ReservationAction.RESERVE
        val text = stringResource(if (reserve) R.string.shared_wish_reserve else R.string.shared_wish_release)
        val description = stringResource(
            if (reserve) R.string.shared_wish_reserve_description else R.string.shared_wish_release_description,
            wishName,
        )
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primaryAction, strokeWidth = 2.dp)
        }
        TextButton(
            onClick = if (reserve) onReserve else onRelease,
            enabled = !busy,
            modifier = Modifier.semantics { contentDescription = description },
            colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
        ) {
            Text(text)
        }
    }
}

@Composable
private fun reservationMessageText(message: ReservationMessage): String = when (message) {
    ReservationMessage.TAKEN_BY_OTHER -> stringResource(R.string.shared_wish_taken)
    ReservationMessage.WISH_UNAVAILABLE -> stringResource(R.string.shared_wish_unavailable)
    ReservationMessage.NETWORK -> stringResource(R.string.sharing_network_error)
    ReservationMessage.SERVER -> stringResource(R.string.sharing_server_error)
}

private fun String.isWebLink(): Boolean = startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

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
                            SharedWish(UUID.randomUUID(), "Wool socks", null, null, null, ReservationState.RESERVED_BY_OTHER),
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
