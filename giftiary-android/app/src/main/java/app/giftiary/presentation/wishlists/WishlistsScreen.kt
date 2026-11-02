package app.giftiary.presentation.wishlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.domain.model.Wishlist
import app.giftiary.presentation.components.GiftiaryAddFab
import app.giftiary.presentation.components.GiftiaryAddFabClearance
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryPillButton
import app.giftiary.presentation.components.GiftiaryRow
import app.giftiary.presentation.components.GiftiaryTextAction
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.RowChevron
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.navigation.hasNavigationRail
import app.giftiary.presentation.sharing.OnlineContent
import app.giftiary.presentation.sharing.OnlineState
import app.giftiary.presentation.sharing.SharedWishlistsViewModel
import app.giftiary.presentation.sharing.memberName
import app.giftiary.presentation.sharing.retryableFailureText
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.util.UUID

@Composable
fun WishlistsScreen(
    onAddWishlist: () -> Unit,
    onOpenWishlist: (UUID) -> Unit,
    onOpenShared: (UUID) -> Unit,
    onJoin: () -> Unit,
    viewModel: WishlistsViewModel = hiltViewModel(),
    sharedViewModel: SharedWishlistsViewModel = hiltViewModel(),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val shared by sharedViewModel.state.collectAsStateWithLifecycle()
    // Shared lists live on the server: every visit asks again, so one withdrawn meanwhile disappears.
    LifecycleResumeEffect(sharedViewModel) {
        sharedViewModel.refresh()
        onPauseOrDispose {}
    }
    WishlistsScreen(
        content = content,
        shared = shared,
        actions = ListsActions(
            onAddWishlist = onAddWishlist,
            onOpenWishlist = onOpenWishlist,
            onOpenShared = onOpenShared,
            onJoin = onJoin,
            onRetryShared = sharedViewModel::refresh,
            onSharedRefreshFailureShown = sharedViewModel::onRefreshFailureShown,
        ),
    )
}

class ListsActions(
    val onAddWishlist: () -> Unit,
    val onOpenWishlist: (UUID) -> Unit,
    val onOpenShared: (UUID) -> Unit,
    val onJoin: () -> Unit,
    val onRetryShared: () -> Unit,
    val onSharedRefreshFailureShown: () -> Unit,
)

/**
 * The user's own lists, kept on the device, and below them the lists shared with the user, read
 * from the server. Either can be shown without the other.
 */
@Composable
fun WishlistsScreen(
    content: WishlistsContent,
    shared: OnlineState<List<SharedWishlistSummary>>,
    actions: ListsActions,
    modifier: Modifier = Modifier,
    // Phones add a list through the global action; beside a rail there is none.
    showAddButton: Boolean = LocalWidthClass.current.hasNavigationRail,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailure = shared.refreshFailure?.let { retryableFailureText(it) }
    LaunchedEffect(shared.refreshFailure) {
        if (refreshFailure != null) {
            snackbarHostState.showSnackbar(refreshFailure)
            actions.onSharedRefreshFailureShown()
        }
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (showAddButton) GiftiaryAddFab(text = stringResource(R.string.wishlists_add), onClick = actions.onAddWishlist)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier
            .padding(contentPadding)
            .windowInsetsPadding(WindowInsets.statusBars)
        when (content) {
            WishlistsContent.Loading -> LoadingContent(contentModifier)
            // Nothing could be read, so there are no saved lists this message would hide.
            WishlistsContent.LoadFailed -> MessageContent(stringResource(R.string.wishlists_load_failed), contentModifier)
            is WishlistsContent.Loaded -> LazyColumn(
                modifier = contentModifier
                    .fillMaxSize()
                    .giftiaryReadableWidth(),
                contentPadding = PaddingValues(bottom = if (showAddButton) GiftiaryAddFabClearance else GiftiarySpacing.xl),
            ) {
                item { ListsHeader() }
                ownLists(content, actions)
                sharedLists(shared, actions)
            }
        }
    }
}

@Composable
private fun ListsHeader() {
    val colors = GiftiaryTheme.colors
    Column(modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.section)) {
        Text(
            text = stringResource(R.string.lists_title),
            modifier = Modifier.semantics { heading() },
            style = GiftiaryType.screenTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.lists_subtitle),
            modifier = Modifier.padding(top = 6.dp),
            style = GiftiaryType.bodySmall,
            color = colors.textSecondary,
        )
    }
}

private fun LazyListScope.ownLists(content: WishlistsContent.Loaded, actions: ListsActions) {
    item { SectionTitle(stringResource(R.string.lists_section_yours), modifier = Modifier.padding(top = GiftiarySpacing.xl)) }
    if (content.isEmpty) {
        item { EmptyLists(onAddWishlist = actions.onAddWishlist) }
        return
    }
    val rows = content.own.map { it to null } + content.forPeople.map { it.wishlist to (it.personName?.let(WishlistOwner::Person) ?: WishlistOwner.PersonRemoved) }
    itemsIndexed(rows, key = { _, (wishlist, _) -> wishlist.id }) { index, (wishlist, owner) ->
        if (index > 0) ListDivider(leading = 64.dp)
        OwnListRow(wishlist = wishlist, owner = owner, wishCount = content.wishCount(wishlist), onClick = { actions.onOpenWishlist(wishlist.id) })
    }
}

/**
 * [owner] is null for the user's own list. Whether an own list is shared is only known online, on
 * its sharing screen, so the row claims nothing about it; a list kept for a person can never be
 * shared, so it is only the user's.
 */
@Composable
private fun OwnListRow(wishlist: Wishlist, owner: WishlistOwner?, wishCount: Int, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    GiftiaryRow(
        title = wishlist.name,
        leading = { ListCover(wishlist.name) },
        trailing = { RowChevron() },
        supporting = {
            if (owner != null) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.textLabel)
                    Text(stringResource(R.string.lists_only_you), style = GiftiaryType.label.copy(fontWeight = GiftiaryType.metadata.fontWeight), color = colors.textLabel)
                }
            }
            Text(
                text = listOfNotNull(owner?.let { wishlistOwnerText(it) }, wishCountText(wishCount)).joinToString(" · "),
                style = GiftiaryType.metadata,
                color = colors.textMuted,
            )
        },
        contentPadding = PaddingValues(horizontal = GiftiarySpacing.browseGutter, vertical = 14.dp),
        onClickLabel = stringResource(R.string.wishlist_open),
        onClick = onClick,
    )
}

@Composable
private fun wishCountText(count: Int): String =
    if (count == 0) {
        stringResource(R.string.person_detail_wishlist_no_wishes)
    } else {
        pluralStringResource(R.plurals.person_detail_wish_count, count, count)
    }

@Composable
private fun EmptyLists(onAddWishlist: () -> Unit) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier.padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
    ) {
        Text(stringResource(R.string.wishlists_empty_title), style = GiftiaryType.contentTitle, color = colors.textPrimary)
        Text(stringResource(R.string.wishlists_empty_message), style = GiftiaryType.bodySmall, color = colors.textSecondary)
        GiftiaryPillButton(
            text = stringResource(R.string.wishlists_add),
            onClick = onAddWishlist,
            modifier = Modifier.padding(top = GiftiarySpacing.m),
            leadingIcon = painterResource(R.drawable.ic_add),
        )
    }
}

private fun LazyListScope.sharedLists(shared: OnlineState<List<SharedWishlistSummary>>, actions: ListsActions) {
    item { SharedHeading(onJoin = actions.onJoin) }
    when (val content = shared.content) {
        OnlineContent.Loading -> item { SharedProgress() }
        is OnlineContent.Failed -> item {
            // Only this section depends on the connection; the user's own lists above stay usable.
            SharedNote(retryableFailureText(content.failure)) {
                GiftiaryTextAction(text = stringResource(R.string.sharing_retry), onClick = actions.onRetryShared, underline = true)
            }
        }
        is OnlineContent.Loaded -> {
            if (shared.isRefreshing) item { SharedProgress() }
            if (content.value.isEmpty()) item { SharedNote(stringResource(R.string.shared_wishlists_empty_message)) }
            itemsIndexed(content.value, key = { _, wishlist -> "shared-${wishlist.id}" }) { index, wishlist ->
                if (index > 0) ListDivider(leading = 48.dp)
                SharedListRow(wishlist = wishlist, onClick = { actions.onOpenShared(wishlist.id) })
            }
        }
    }
}

/** The aqua chip is the section's only contextual colour: it marks sharing without tinting a row. */
@Composable
private fun SharedHeading(onJoin: () -> Unit) {
    val colors = GiftiaryTheme.colors
    val joinDescription = stringResource(R.string.lists_join_description)
    Column(modifier = Modifier.padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.s, top = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(colors.avatarFallback, GiftiaryShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_people), contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.textLabel)
            }
            Spacer(Modifier.size(GiftiarySpacing.s))
            Text(
                text = stringResource(R.string.shared_wishlists_title),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = GiftiaryType.sectionTitle,
                color = colors.textPrimary,
            )
            GiftiaryTextAction(
                text = stringResource(R.string.shared_wishlists_join),
                onClick = onJoin,
                modifier = Modifier.semantics { contentDescription = joinDescription },
            )
        }
        Text(
            text = stringResource(R.string.lists_shared_subtitle),
            modifier = Modifier.padding(start = 36.dp),
            style = GiftiaryType.metadata,
            color = colors.textSecondary,
        )
    }
}

/** The server does not report how many wishes a shared list has, so the row does not say. */
@Composable
private fun SharedListRow(wishlist: SharedWishlistSummary, onClick: () -> Unit) {
    val owner = memberName(wishlist.ownerDisplayName)
    GiftiaryRow(
        title = wishlist.name,
        leading = { GiftiaryAvatar(name = owner) },
        trailing = { RowChevron() },
        supporting = {
            Text(owner, style = GiftiaryType.label.copy(fontWeight = GiftiaryType.metadata.fontWeight), color = GiftiaryTheme.colors.textLabel)
        },
        contentPadding = PaddingValues(horizontal = GiftiarySpacing.browseGutter, vertical = 14.dp),
        onClickLabel = stringResource(R.string.wishlist_open),
        onClick = onClick,
    )
}

@Composable
private fun SharedProgress() {
    LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GiftiarySpacing.browseGutter, vertical = GiftiarySpacing.m),
        color = GiftiaryTheme.colors.primaryAction,
        trackColor = GiftiaryTheme.colors.divider,
    )
}

@Composable
private fun SharedNote(text: String, action: @Composable () -> Unit = {}) {
    Column(modifier = Modifier.padding(start = GiftiarySpacing.browseGutter + 36.dp, end = GiftiarySpacing.browseGutter, top = GiftiarySpacing.m)) {
        Text(text = text, style = GiftiaryType.bodySmall, color = GiftiaryTheme.colors.textSecondary)
        action()
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(horizontal = GiftiarySpacing.browseGutter)
            .semantics { heading() },
        style = GiftiaryType.sectionTitle,
        color = GiftiaryTheme.colors.textPrimary,
    )
}

@Composable
private fun ListDivider(leading: Dp) {
    GiftiaryDivider(
        modifier = Modifier.padding(end = GiftiarySpacing.browseGutter),
        startIndent = GiftiarySpacing.browseGutter + leading + 14.dp,
    )
}

@PreviewLightDark
@Composable
private fun WishlistsPreview() {
    fun wishlist(name: String, personId: UUID? = null) = Wishlist(UUID.randomUUID(), personId, name, Instant.EPOCH, Instant.EPOCH)
    val mine = wishlist("My wishlist")
    GiftiaryTheme {
        WishlistsScreen(
            content = WishlistsContent.Loaded(
                own = listOf(mine),
                forPeople = listOf(WishlistOfPerson(wishlist("Martin’s wishlist", UUID.randomUUID()), "Martin")),
                wishCounts = mapOf(mine.id to 8),
            ),
            shared = OnlineState(OnlineContent.Loaded(listOf(SharedWishlistSummary(UUID.randomUUID(), "Peter’s wishlist", "Peter")))),
            actions = ListsActions({}, {}, {}, {}, {}, {}),
        )
    }
}
