package app.giftiary.presentation.wishlists

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Wishlist
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.util.UUID

@Composable
fun WishlistsScreen(
    onBack: () -> Unit,
    onAddWishlist: () -> Unit,
    onOpenWishlist: (UUID) -> Unit,
    onOpenShared: () -> Unit,
    viewModel: WishlistsViewModel = hiltViewModel(),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    WishlistsScreen(
        content = content,
        onBack = onBack,
        onAddWishlist = onAddWishlist,
        onOpenWishlist = onOpenWishlist,
        onOpenShared = onOpenShared,
    )
}

@Composable
fun WishlistsScreen(
    content: WishlistsContent,
    onBack: () -> Unit,
    onAddWishlist: () -> Unit,
    onOpenWishlist: (UUID) -> Unit,
    onOpenShared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            GiftiaryTopBar(
                title = stringResource(R.string.wishlists_title),
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = onOpenShared,
                        colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.textPrimary),
                    ) {
                        Text(stringResource(R.string.wishlists_open_shared))
                    }
                },
            )
        },
        floatingActionButton = { AddButton(text = stringResource(R.string.wishlists_add), onClick = onAddWishlist) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (content) {
            WishlistsContent.Loading -> LoadingContent(contentModifier)
            WishlistsContent.LoadFailed -> MessageContent(stringResource(R.string.wishlists_load_failed), contentModifier)
            is WishlistsContent.Loaded -> if (content.isEmpty) {
                MessageContent(
                    title = stringResource(R.string.wishlists_empty_title),
                    message = stringResource(R.string.wishlists_empty_message),
                    modifier = contentModifier,
                )
            } else {
                WishlistsList(content = content, onOpenWishlist = onOpenWishlist, modifier = contentModifier)
            }
        }
    }
}

@Composable
private fun WishlistsList(content: WishlistsContent.Loaded, onOpenWishlist: (UUID) -> Unit, modifier: Modifier = Modifier) {
    val dividerColor = GiftiaryTheme.colors.divider
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = FAB_CLEARANCE)) {
        if (content.own.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.wishlists_section_own)) }
            items(content.own, key = { it.id }) { wishlist ->
                ListRow(title = wishlist.name, detail = null, onClick = { onOpenWishlist(wishlist.id) })
                HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = dividerColor)
            }
        }
        if (content.forPeople.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.wishlists_section_people)) }
            items(content.forPeople, key = { it.wishlist.id }) { entry ->
                val owner = entry.personName?.let { WishlistOwner.Person(it) } ?: WishlistOwner.PersonRemoved
                ListRow(
                    title = entry.wishlist.name,
                    detail = wishlistOwnerText(owner),
                    onClick = { onOpenWishlist(entry.wishlist.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = dividerColor)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun WishlistsPreview() {
    fun wishlist(name: String, personId: UUID? = null) = Wishlist(UUID.randomUUID(), personId, name, Instant.EPOCH, Instant.EPOCH)
    GiftiaryTheme {
        WishlistsScreen(
            content = WishlistsContent.Loaded(
                own = listOf(wishlist("Birthday 2027")),
                forPeople = listOf(
                    WishlistOfPerson(wishlist("Anna's Christmas", UUID.randomUUID()), "Anna Kovács"),
                    WishlistOfPerson(wishlist("Old list", UUID.randomUUID()), null),
                ),
            ),
            onBack = {},
            onAddWishlist = {},
            onOpenWishlist = {},
            onOpenShared = {},
        )
    }
}
