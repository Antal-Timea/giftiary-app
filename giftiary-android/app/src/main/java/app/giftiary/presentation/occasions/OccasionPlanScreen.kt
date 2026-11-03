package app.giftiary.presentation.occasions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryPillButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.giftideas.GiftIdeaRow
import app.giftiary.presentation.gifts.DetailContextCard
import app.giftiary.presentation.home.DaysCountdown
import app.giftiary.presentation.home.occasionPlanText
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.util.UUID

@Composable
fun OccasionPlanScreen(
    viewModel: OccasionPlanViewModel,
    onBack: () -> Unit,
    onEditOccasion: (UUID) -> Unit,
    onOpenPerson: (UUID) -> Unit,
    onOpenGiftIdea: (UUID) -> Unit,
    onAddGiftIdea: (occasion: Occasion) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OccasionPlanScreen(
        state = state,
        onBack = onBack,
        onEditOccasion = { onEditOccasion(it.id) },
        onOpenPerson = { onOpenPerson(it.id) },
        onOpenGiftIdea = { onOpenGiftIdea(it.idea.id) },
        onAddGiftIdea = onAddGiftIdea,
    )
}

/**
 * An occasion and the ideas gathered for it, by analogy with the approved detail pages (there is
 * no board): the occasion as the title, whose it is, when it comes on aqua with Add gift idea,
 * then the ideas. Editing the occasion stays in the menu, away from the planning.
 */
@Composable
fun OccasionPlanScreen(
    state: OccasionPlanUiState,
    onBack: () -> Unit,
    onEditOccasion: (Occasion) -> Unit,
    onOpenPerson: (Person) -> Unit,
    onOpenGiftIdea: (GiftIdeaEntry) -> Unit,
    onAddGiftIdea: (Occasion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, containerColor = GiftiaryTheme.colors.background) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state) {
            OccasionPlanUiState.Loading -> GiftiaryTaskStatus(null, onBack, contentModifier)
            OccasionPlanUiState.LoadFailed ->
                GiftiaryTaskStatus(stringResource(R.string.occasion_editor_load_failed), onBack, contentModifier)
            OccasionPlanUiState.NotFound ->
                GiftiaryTaskStatus(stringResource(R.string.occasion_editor_not_found), onBack, contentModifier)
            is OccasionPlanUiState.Loaded -> PlanContent(
                state = state,
                onBack = onBack,
                onEditOccasion = { onEditOccasion(state.occasion) },
                onOpenPerson = onOpenPerson,
                onOpenGiftIdea = onOpenGiftIdea,
                onAddGiftIdea = { onAddGiftIdea(state.occasion) },
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun PlanContent(
    state: OccasionPlanUiState.Loaded,
    onBack: () -> Unit,
    onEditOccasion: () -> Unit,
    onOpenPerson: (Person) -> Unit,
    onOpenGiftIdea: (GiftIdeaEntry) -> Unit,
    onAddGiftIdea: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .giftiaryReadableWidth(),
        contentPadding = PaddingValues(bottom = GiftiarySpacing.xxl),
    ) {
        item {
            GiftiaryTaskHeader(
                title = occasionTitle(state.occasion),
                subtitle = null,
                onBack = onBack,
                modifier = Modifier.padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter),
                eyebrow = stringResource(R.string.occasion_plan_eyebrow).uppercase(),
                menu = {
                    OptionsMenu(R.drawable.ic_more_vertical, stringResource(R.string.task_more)) {
                        Option(stringResource(R.string.occasion_editor_edit_title), R.drawable.ic_edit, onEditOccasion)
                    }
                },
                // A general occasion is the user's own: no one is shown for it.
                context = state.person?.let { person ->
                    {
                        DetailContextCard(
                            name = person.name,
                            title = person.name,
                            onClickLabel = stringResource(R.string.people_open),
                            onClick = { onOpenPerson(person) },
                        )
                    }
                },
            )
        }
        item { WhenBand(state = state, onAddGiftIdea = onAddGiftIdea, modifier = Modifier.padding(top = GiftiarySpacing.xl)) }
        item {
            Text(
                text = stringResource(R.string.person_detail_gift_ideas),
                modifier = Modifier
                    .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.xl, bottom = GiftiarySpacing.xs)
                    .semantics { heading() },
                style = GiftiaryType.sectionTitle,
                color = GiftiaryTheme.colors.textPrimary,
            )
        }
        when (val ideas = state.ideas) {
            OccasionIdeas.Unavailable -> item { Note(stringResource(R.string.occasion_plan_ideas_unavailable)) }
            is OccasionIdeas.Loaded -> if (ideas.ideas.isEmpty()) {
                item { Note(stringResource(R.string.occasion_plan_no_ideas)) }
            } else {
                items(ideas.ideas, key = { it.idea.id }) { entry ->
                    // A general occasion gathers ideas for different people, so each says whose it is.
                    // The row pads itself by 16dp; this lines its text up with the header's gutter.
                    GiftIdeaRow(
                        entry = entry,
                        onClick = { onOpenGiftIdea(entry) },
                        modifier = Modifier.padding(horizontal = GiftiarySpacing.taskGutter - GiftiarySpacing.l),
                        showRecipient = state.person == null,
                    )
                    GiftiaryDivider(modifier = Modifier.padding(horizontal = GiftiarySpacing.taskGutter))
                }
            }
        }
    }
}

/** When it comes and how far planning is, on aqua as a person's next occasion is, with the one coral action. */
@Composable
private fun WhenBand(state: OccasionPlanUiState.Loaded, onAddGiftIdea: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContext, GiftiaryShapes.band)
            .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = 18.dp, bottom = GiftiarySpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs)) {
            Text(text = whenLine(state), style = GiftiaryType.sectionTitle, color = colors.textPrimary)
            occasionPlanText(LocalResources.current, state.plan)?.let {
                Text(text = it, style = GiftiaryType.bodySmall, color = colors.textSecondary)
            }
            GiftiaryPillButton(
                text = stringResource(R.string.gift_ideas_add),
                onClick = onAddGiftIdea,
                modifier = Modifier.padding(top = GiftiarySpacing.m),
                leadingIcon = painterResource(R.drawable.ic_add),
            )
        }
        state.daysUntil?.let { DaysCountdown(days = it, large = true) }
    }
}

/**
 * The next date and the age turned on it, or, for a one-time occasion that has passed, its date.
 * How soon it is stands beside it as the countdown.
 */
@Composable
private fun whenLine(state: OccasionPlanUiState.Loaded): String {
    val next = state.nextDate ?: return occasionDateLine(state.occasion)
    return listOfNotNull(
        occurrenceDate(next),
        state.turningAge?.let { stringResource(R.string.coming_up_turns, it) },
    ).joinToString(" · ")
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = GiftiarySpacing.taskGutter, vertical = GiftiarySpacing.s),
        style = GiftiaryType.bodySmall,
        color = GiftiaryTheme.colors.textSecondary,
    )
}
