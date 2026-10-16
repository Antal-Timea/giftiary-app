package app.giftiary.presentation.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.model.Wishlist
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.giftideas.GiftIdeaRow
import app.giftiary.presentation.gifts.GiftRow
import app.giftiary.presentation.occasions.OccasionRow
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.wishlists.ListRow
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.util.UUID

@Composable
fun PersonDetailScreen(
    viewModel: PersonDetailViewModel,
    onBack: () -> Unit,
    onEditPerson: (UUID) -> Unit,
    onAddOccasion: (UUID) -> Unit,
    onEditOccasion: (UUID) -> Unit,
    onAddGift: (UUID) -> Unit,
    onOpenGift: (UUID) -> Unit,
    onAddGiftIdea: (UUID) -> Unit,
    onOpenGiftIdea: (UUID) -> Unit,
    onAddWishlist: (UUID) -> Unit,
    onOpenWishlist: (UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PersonDetailScreen(
        state = state,
        onBack = onBack,
        onEditPerson = { onEditPerson(it.id) },
        onAddOccasion = { onAddOccasion(it.id) },
        onEditOccasion = { onEditOccasion(it.id) },
        onAddGift = { onAddGift(it.id) },
        onOpenGift = { onOpenGift(it.gift.id) },
        onAddGiftIdea = { onAddGiftIdea(it.id) },
        onOpenGiftIdea = { onOpenGiftIdea(it.idea.id) },
        onAddWishlist = { onAddWishlist(it.id) },
        onOpenWishlist = { onOpenWishlist(it.wishlist.id) },
    )
}

@Composable
fun PersonDetailScreen(
    state: PersonDetailUiState,
    onBack: () -> Unit,
    onEditPerson: (Person) -> Unit,
    onAddOccasion: (Person) -> Unit,
    onEditOccasion: (Occasion) -> Unit,
    onAddGift: (Person) -> Unit,
    onOpenGift: (GiftEntry) -> Unit,
    onAddGiftIdea: (Person) -> Unit,
    onOpenGiftIdea: (GiftIdeaEntry) -> Unit,
    onAddWishlist: (Person) -> Unit,
    onOpenWishlist: (PersonWishlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = state as? PersonDetailUiState.Loaded
    Scaffold(
        modifier = modifier,
        topBar = {
            GiftiaryTopBar(
                title = loaded?.person?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (loaded != null) {
                        TextButton(
                            onClick = { onEditPerson(loaded.person) },
                            colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.textPrimary),
                        ) {
                            Text(stringResource(R.string.people_edit))
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state) {
            PersonDetailUiState.Loading -> LoadingContent(contentModifier)
            PersonDetailUiState.NotFound -> MessageContent(stringResource(R.string.person_editor_not_found), contentModifier)
            PersonDetailUiState.LoadFailed -> MessageContent(stringResource(R.string.person_editor_load_failed), contentModifier)
            is PersonDetailUiState.Loaded -> PersonDetailContent(
                state = state,
                onAddOccasion = { onAddOccasion(state.person) },
                onEditOccasion = onEditOccasion,
                onAddGift = { onAddGift(state.person) },
                onOpenGift = onOpenGift,
                onAddGiftIdea = { onAddGiftIdea(state.person) },
                onOpenGiftIdea = onOpenGiftIdea,
                onAddWishlist = { onAddWishlist(state.person) },
                onOpenWishlist = onOpenWishlist,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun PersonDetailContent(
    state: PersonDetailUiState.Loaded,
    onAddOccasion: () -> Unit,
    onEditOccasion: (Occasion) -> Unit,
    onAddGift: () -> Unit,
    onOpenGift: (GiftEntry) -> Unit,
    onAddGiftIdea: () -> Unit,
    onOpenGiftIdea: (GiftIdeaEntry) -> Unit,
    onAddWishlist: () -> Unit,
    onOpenWishlist: (PersonWishlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = GiftiarySpacing.xxl),
    ) {
        state.person.relationship?.let { relationship ->
            item {
                Text(
                    text = relationship,
                    modifier = Modifier.padding(horizontal = GiftiarySpacing.l),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
        }
        item { SectionHeading(stringResource(R.string.person_detail_occasions)) }
        if (state.occasions.isEmpty()) {
            item { NoOccasions() }
        } else {
            items(state.occasions, key = { it.id }) { occasion ->
                OccasionRow(occasion = occasion, onClick = { onEditOccasion(occasion) })
                HorizontalDivider(
                    modifier = Modifier.padding(start = GiftiarySpacing.l),
                    color = colors.divider,
                )
            }
        }
        item { AddButton(label = stringResource(R.string.person_detail_add_occasion), onClick = onAddOccasion) }

        item { SectionHeading(stringResource(R.string.person_detail_gift_ideas)) }
        if (state.giftIdeas.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.person_detail_no_gift_ideas),
                    modifier = Modifier.padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        } else {
            items(state.giftIdeas, key = { it.idea.id }) { entry ->
                GiftIdeaRow(entry = entry, onClick = { onOpenGiftIdea(entry) }, showRecipient = false)
                HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
            }
        }
        item { AddButton(label = stringResource(R.string.person_detail_add_gift_idea), onClick = onAddGiftIdea) }

        item { SectionHeading(stringResource(R.string.person_detail_wishlists)) }
        if (state.wishlists.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.person_detail_no_wishlists),
                    modifier = Modifier.padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        } else {
            items(state.wishlists, key = { it.wishlist.id }) { entry ->
                ListRow(
                    title = entry.wishlist.name,
                    detail = if (entry.wishCount == 0) {
                        stringResource(R.string.person_detail_wishlist_no_wishes)
                    } else {
                        pluralStringResource(R.plurals.person_detail_wish_count, entry.wishCount, entry.wishCount)
                    },
                    onClick = { onOpenWishlist(entry) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
            }
        }
        item { AddButton(label = stringResource(R.string.person_detail_add_wishlist), onClick = onAddWishlist) }

        item { SectionHeading(stringResource(R.string.person_detail_gifts)) }
        if (state.gifts.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.person_detail_no_gifts),
                    modifier = Modifier.padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        } else {
            items(state.gifts, key = { it.gift.id }) { entry ->
                // The person is obvious here; only a gift between two other people needs both names.
                GiftRow(
                    entry = entry,
                    onClick = { onOpenGift(entry) },
                    showPeople = entry.giver != null && entry.recipient != null,
                )
                HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
            }
        }
        item { AddButton(label = stringResource(R.string.person_detail_add_gift), onClick = onAddGift) }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = GiftiarySpacing.l, end = GiftiarySpacing.l, top = GiftiarySpacing.xl, bottom = GiftiarySpacing.s)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = GiftiaryTheme.colors.textPrimary,
    )
}

@Composable
private fun NoOccasions() {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = Modifier.padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
    ) {
        Text(
            text = stringResource(R.string.person_detail_no_occasions_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.person_detail_no_occasions_message),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = GiftiarySpacing.s, vertical = GiftiarySpacing.s),
        colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.primaryActionText),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
            Spacer(Modifier.width(GiftiarySpacing.s))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val previewPerson = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)

private fun previewOccasion(type: OccasionType, day: Int, month: Int, year: Int? = null, title: String? = null) =
    Occasion(UUID.randomUUID(), previewPerson.id, type, title, day, month, year, true, Instant.EPOCH, Instant.EPOCH)

@PreviewLightDark
@Composable
private fun PersonDetailPreview() {
    GiftiaryTheme {
        PersonDetailScreen(
            state = PersonDetailUiState.Loaded(
                person = previewPerson,
                occasions = listOf(
                    previewOccasion(OccasionType.BIRTHDAY, 12, 3, year = 1990),
                    previewOccasion(OccasionType.ANNIVERSARY, 18, 6),
                    previewOccasion(OccasionType.NAME_DAY, 26, 7),
                    previewOccasion(OccasionType.CUSTOM, 1, 9, title = "First day at school"),
                ),
                giftIdeas = emptyList(),
                gifts = emptyList(),
                wishlists = listOf(
                    PersonWishlist(Wishlist(UUID.randomUUID(), previewPerson.id, "Christmas", Instant.EPOCH, Instant.EPOCH), 3),
                    PersonWishlist(Wishlist(UUID.randomUUID(), previewPerson.id, "Birthday", Instant.EPOCH, Instant.EPOCH), 0),
                ),
            ),
            onBack = {},
            onEditPerson = {},
            onAddOccasion = {},
            onEditOccasion = {},
            onAddGift = {},
            onOpenGift = {},
            onAddGiftIdea = {},
            onOpenGiftIdea = {},
            onAddWishlist = {},
            onOpenWishlist = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PersonDetailNoOccasionsPreview() {
    GiftiaryTheme {
        PersonDetailScreen(
            state = PersonDetailUiState.Loaded(previewPerson, emptyList(), emptyList(), emptyList(), emptyList()),
            onBack = {},
            onEditPerson = {},
            onAddOccasion = {},
            onEditOccasion = {},
            onAddGift = {},
            onOpenGift = {},
            onAddGiftIdea = {},
            onOpenGiftIdea = {},
            onAddWishlist = {},
            onOpenWishlist = {},
        )
    }
}
