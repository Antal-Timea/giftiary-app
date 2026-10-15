package app.gifty.presentation.people

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.model.Occasion
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.Person
import app.gifty.domain.repository.GiftEntry
import app.gifty.domain.repository.GiftIdeaEntry
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.giftideas.GiftIdeaRow
import app.gifty.presentation.gifts.GiftRow
import app.gifty.presentation.occasions.OccasionRow
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme
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
    modifier: Modifier = Modifier,
) {
    val loaded = state as? PersonDetailUiState.Loaded
    Scaffold(
        modifier = modifier,
        topBar = {
            GiftyTopBar(
                title = loaded?.person?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (loaded != null) {
                        TextButton(
                            onClick = { onEditPerson(loaded.person) },
                            colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.textPrimary),
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
    modifier: Modifier = Modifier,
) {
    val colors = GiftyTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = GiftySpacing.xxl),
    ) {
        state.person.relationship?.let { relationship ->
            item {
                Text(
                    text = relationship,
                    modifier = Modifier.padding(horizontal = GiftySpacing.l),
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
                    modifier = Modifier.padding(start = GiftySpacing.l),
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
                    modifier = Modifier.padding(horizontal = GiftySpacing.l, vertical = GiftySpacing.s),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        } else {
            items(state.giftIdeas, key = { it.idea.id }) { entry ->
                GiftIdeaRow(entry = entry, onClick = { onOpenGiftIdea(entry) }, showRecipient = false)
                HorizontalDivider(modifier = Modifier.padding(start = GiftySpacing.l), color = colors.divider)
            }
        }
        item { AddButton(label = stringResource(R.string.person_detail_add_gift_idea), onClick = onAddGiftIdea) }

        item { SectionHeading(stringResource(R.string.person_detail_gifts)) }
        if (state.gifts.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.person_detail_no_gifts),
                    modifier = Modifier.padding(horizontal = GiftySpacing.l, vertical = GiftySpacing.s),
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
                HorizontalDivider(modifier = Modifier.padding(start = GiftySpacing.l), color = colors.divider)
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
            .padding(start = GiftySpacing.l, end = GiftySpacing.l, top = GiftySpacing.xl, bottom = GiftySpacing.s)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = GiftyTheme.colors.textPrimary,
    )
}

@Composable
private fun NoOccasions() {
    val colors = GiftyTheme.colors
    Column(
        modifier = Modifier.padding(horizontal = GiftySpacing.l, vertical = GiftySpacing.s),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs),
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
        modifier = Modifier.padding(horizontal = GiftySpacing.s, vertical = GiftySpacing.s),
        colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.primaryActionText),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
            Spacer(Modifier.width(GiftySpacing.s))
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
    GiftyTheme {
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
            ),
            onBack = {},
            onEditPerson = {},
            onAddOccasion = {},
            onEditOccasion = {},
            onAddGift = {},
            onOpenGift = {},
            onAddGiftIdea = {},
            onOpenGiftIdea = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PersonDetailNoOccasionsPreview() {
    GiftyTheme {
        PersonDetailScreen(
            state = PersonDetailUiState.Loaded(previewPerson, emptyList(), emptyList(), emptyList()),
            onBack = {},
            onEditPerson = {},
            onAddOccasion = {},
            onEditOccasion = {},
            onAddGift = {},
            onOpenGift = {},
            onAddGiftIdea = {},
            onOpenGiftIdea = {},
        )
    }
}
