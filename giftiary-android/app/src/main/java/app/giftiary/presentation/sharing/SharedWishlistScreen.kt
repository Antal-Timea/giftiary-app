package app.giftiary.presentation.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
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
import app.giftiary.presentation.gifts.ProductLink
import app.giftiary.presentation.gifts.giftPrice
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.wishlists.SectionHeading
import java.util.UUID

@Composable
fun SharedWishlistScreen(viewModel: SharedWishlistViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leaving by viewModel.leaving.collectAsStateWithLifecycle()
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
        actions = SharedWishlistActions(
            onBack = onBack,
            onRetry = viewModel::refresh,
            onRefreshFailureShown = viewModel::onRefreshFailureShown,
            onLeaveRequested = viewModel::onLeaveRequested,
            onLeaveConfirmed = viewModel::onLeaveConfirmed,
            onLeaveDismissed = viewModel::onLeaveDismissed,
            onLeaveFailureShown = viewModel::onLeaveFailureShown,
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
)

/** Read-only: the wishes as the owner keeps them; the only action is leaving the list. */
@Composable
fun SharedWishlistScreen(
    state: OnlineState<SharedWishlist>,
    leaving: LeaveState,
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
                onLeave = actions.onLeaveRequested,
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
}

@Composable
private fun SharedWishes(
    wishlist: SharedWishlist,
    isRefreshing: Boolean,
    isLeaving: Boolean,
    onLeave: () -> Unit,
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
            SharedWishRow(wish)
            HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
        }
        item {
            // Quiet, as deleting is on the user's own lists.
            TextButton(
                onClick = onLeave,
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

// Reservation state is deliberately not shown: reserving comes with its own actions later.
@Composable
private fun SharedWishRow(wish: SharedWish) {
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
    }
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
            actions = SharedWishlistActions({}, {}, {}, {}, {}, {}, {}),
        )
    }
}
