package app.gifty.presentation.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.model.Occasion
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.Person
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.occasions.occasionDate
import app.gifty.presentation.occasions.occasionTitle
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PersonDetailScreen(
        state = state,
        onBack = onBack,
        onEditPerson = { onEditPerson(it.id) },
        onAddOccasion = { onAddOccasion(it.id) },
        onEditOccasion = { onEditOccasion(it.id) },
    )
}

@Composable
fun PersonDetailScreen(
    state: PersonDetailUiState,
    onBack: () -> Unit,
    onEditPerson: (Person) -> Unit,
    onAddOccasion: (Person) -> Unit,
    onEditOccasion: (Occasion) -> Unit,
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
        item {
            Text(
                text = stringResource(R.string.person_detail_occasions),
                modifier = Modifier
                    .padding(start = GiftySpacing.l, end = GiftySpacing.l, top = GiftySpacing.xl, bottom = GiftySpacing.s)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        }
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
        item { AddOccasionButton(onClick = onAddOccasion) }
    }
}

@Composable
private fun OccasionRow(occasion: Occasion, onClick: () -> Unit) {
    val colors = GiftyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.people_edit), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = GiftySpacing.l, vertical = GiftySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs),
    ) {
        Text(
            text = occasionTitle(occasion),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        val date = occasionDate(occasion)
        Text(
            text = if (occasion.repeatsYearly) date else stringResource(R.string.occasion_date_one_time, date),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
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
private fun AddOccasionButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = GiftySpacing.s, vertical = GiftySpacing.s),
        colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.primaryActionText),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
            Spacer(Modifier.width(GiftySpacing.s))
            Text(stringResource(R.string.person_detail_add_occasion), style = MaterialTheme.typography.labelLarge)
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
            ),
            onBack = {},
            onEditPerson = {},
            onAddOccasion = {},
            onEditOccasion = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PersonDetailNoOccasionsPreview() {
    GiftyTheme {
        PersonDetailScreen(
            state = PersonDetailUiState.Loaded(previewPerson, emptyList()),
            onBack = {},
            onEditPerson = {},
            onAddOccasion = {},
            onEditOccasion = {},
        )
    }
}
