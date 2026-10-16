package app.giftiary.presentation.occasions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.giftideas.GiftIdeaRow
import app.giftiary.presentation.home.daysUntilText
import app.giftiary.presentation.home.occasionPlanText
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
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
    val loaded = state as? OccasionPlanUiState.Loaded
    Scaffold(
        modifier = modifier,
        topBar = {
            GiftiaryTopBar(
                title = loaded?.let { occasionTitle(it.occasion) }.orEmpty(),
                onBack = onBack,
                actions = {
                    if (loaded != null) {
                        TextButton(
                            onClick = { onEditOccasion(loaded.occasion) },
                            colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.textPrimary),
                        ) {
                            Text(stringResource(R.string.people_edit))
                        }
                    }
                },
            )
        },
        floatingActionButton = { if (loaded != null) AddIdeaButton(onClick = { onAddGiftIdea(loaded.occasion) }) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (state) {
            OccasionPlanUiState.Loading -> LoadingContent(contentModifier)
            OccasionPlanUiState.LoadFailed -> MessageContent(stringResource(R.string.occasion_editor_load_failed), contentModifier)
            OccasionPlanUiState.NotFound -> MessageContent(stringResource(R.string.occasion_editor_not_found), contentModifier)
            is OccasionPlanUiState.Loaded -> PlanContent(state, onOpenPerson, onOpenGiftIdea, contentModifier)
        }
    }
}

@Composable
private fun PlanContent(
    state: OccasionPlanUiState.Loaded,
    onOpenPerson: (Person) -> Unit,
    onOpenGiftIdea: (GiftIdeaEntry) -> Unit,
    modifier: Modifier,
) {
    val colors = GiftiaryTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
                verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs),
            ) {
                state.person?.let { person ->
                    TextButton(
                        onClick = { onOpenPerson(person) },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
                    ) {
                        Text(person.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(text = whenLine(state), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                occasionPlanText(LocalResources.current, state.plan)?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.person_detail_gift_ideas),
                modifier = Modifier
                    .padding(start = GiftiarySpacing.l, end = GiftiarySpacing.l, top = GiftiarySpacing.xl, bottom = GiftiarySpacing.s)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        }
        when (val ideas = state.ideas) {
            OccasionIdeas.Unavailable -> item { Note(stringResource(R.string.occasion_plan_ideas_unavailable)) }
            is OccasionIdeas.Loaded -> if (ideas.ideas.isEmpty()) {
                item { Note(stringResource(R.string.occasion_plan_no_ideas)) }
            } else {
                items(ideas.ideas, key = { it.idea.id }) { entry ->
                    // A general occasion gathers ideas for different people, so each says whose it is.
                    GiftIdeaRow(entry = entry, onClick = { onOpenGiftIdea(entry) }, showRecipient = state.person == null)
                    HorizontalDivider(modifier = Modifier.padding(start = GiftiarySpacing.l), color = colors.divider)
                }
            }
        }
    }
}

/** The next date with how soon it is, or, for a one-time occasion that has passed, its date. */
@Composable
private fun whenLine(state: OccasionPlanUiState.Loaded): String {
    val next = state.nextDate ?: return stringResource(R.string.occasion_date_one_time, occasionDate(state.occasion))
    return listOfNotNull(
        occurrenceDate(next),
        state.daysUntil?.let { daysUntilText(it) },
        state.turningAge?.let { stringResource(R.string.coming_up_turns, it) },
    ).joinToString(" · ")
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
        style = MaterialTheme.typography.bodyMedium,
        color = GiftiaryTheme.colors.textSecondary,
    )
}

@Composable
private fun AddIdeaButton(onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    // The content overload keeps the label in the semantics tree, as on the People screen.
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = colors.primaryAction,
        contentColor = colors.onPrimaryAction,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    ) {
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
        Spacer(Modifier.width(GiftiarySpacing.m))
        Text(stringResource(R.string.gift_ideas_add))
    }
}

private val FAB_CLEARANCE = 96.dp
