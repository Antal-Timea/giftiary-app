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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.wishlists.ListRow
import java.util.UUID

@Composable
fun SharedWishlistsScreen(
    viewModel: SharedWishlistsViewModel,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onOpenWishlist: (UUID) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Shared lists live on the server: every visit asks again, so one withdrawn meanwhile disappears.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    SharedWishlistsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onJoin = onJoin,
        onOpenWishlist = onOpenWishlist,
        onRefreshFailureShown = viewModel::onRefreshFailureShown,
    )
}

@Composable
fun SharedWishlistsScreen(
    state: OnlineState<List<SharedWishlistSummary>>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onJoin: () -> Unit,
    onOpenWishlist: (UUID) -> Unit,
    onRefreshFailureShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailure = state.refreshFailure?.let { retryableFailureText(it) }
    LaunchedEffect(state.refreshFailure) {
        if (refreshFailure != null) {
            snackbarHostState.showSnackbar(refreshFailure)
            onRefreshFailureShown()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            GiftiaryTopBar(
                title = stringResource(R.string.shared_wishlists_title),
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = onJoin,
                        colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.textPrimary),
                    ) {
                        Text(stringResource(R.string.shared_wishlists_join))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            OnlineContent.Loading -> LoadingContent(contentModifier)
            is OnlineContent.Failed -> FailureContent(retryableFailureText(content.failure), onRetry, contentModifier)
            is OnlineContent.Loaded -> if (content.value.isEmpty()) {
                EmptyShared(onJoin, contentModifier)
            } else {
                SharedList(content.value, state.isRefreshing, onOpenWishlist, contentModifier)
            }
        }
    }
}

@Composable
private fun SharedList(
    wishlists: List<SharedWishlistSummary>,
    isRefreshing: Boolean,
    onOpenWishlist: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = GiftiarySpacing.xxl)) {
        item {
            if (isRefreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.primaryAction)
        }
        items(wishlists, key = { it.id }) { wishlist ->
            ListRow(
                title = wishlist.name,
                detail = sharedWishlistOwnerText(wishlist.ownerDisplayName),
                onClick = { onOpenWishlist(wishlist.id) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
        }
    }
}

@Composable
private fun EmptyShared(onJoin: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = GiftiarySpacing.xxl, vertical = GiftiarySpacing.xl),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.shared_wishlists_empty_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.shared_wishlists_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        GiftiarySecondaryButton(text = stringResource(R.string.shared_wishlists_join_with_link), onClick = onJoin)
    }
}

@PreviewLightDark
@Composable
private fun SharedWishlistsPreview() {
    GiftiaryTheme {
        SharedWishlistsScreen(
            state = OnlineState(
                OnlineContent.Loaded(
                    listOf(
                        SharedWishlistSummary(UUID.randomUUID(), "Anna’s birthday", "Anna"),
                        SharedWishlistSummary(UUID.randomUUID(), "Christmas", null),
                    ),
                ),
            ),
            onBack = {},
            onRetry = {},
            onJoin = {},
            onOpenWishlist = {},
            onRefreshFailureShown = {},
        )
    }
}
