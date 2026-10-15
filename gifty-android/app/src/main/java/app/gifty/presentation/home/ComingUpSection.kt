package app.gifty.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.gifty.R
import app.gifty.domain.model.UpcomingOccasion
import app.gifty.domain.usecase.ObserveUpcomingOccasionsUseCase
import app.gifty.presentation.occasions.occasionTitle
import app.gifty.presentation.occasions.occurrenceDate
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme

@Composable
fun ComingUpSection(
    content: ComingUpContent,
    onOpen: (ComingUpTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Until the local read answers there is nothing honest to show, and it answers at once.
    if (content == ComingUpContent.Loading) return
    val colors = GiftyTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.coming_up_title),
            modifier = Modifier
                .padding(bottom = GiftySpacing.s)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        when (content) {
            ComingUpContent.Loading -> Unit
            ComingUpContent.Unavailable -> Message(stringResource(R.string.coming_up_unavailable))
            is ComingUpContent.Loaded -> if (content.items.isEmpty()) {
                val days = ObserveUpcomingOccasionsUseCase.WINDOW_DAYS
                Message(pluralStringResource(R.plurals.coming_up_empty, days, days))
            } else {
                content.items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = colors.divider)
                    ComingUpRow(item = item, onClick = { onOpen(item.target) })
                }
            }
        }
    }
}

@Composable
private fun ComingUpRow(item: ComingUpItem, onClick: () -> Unit) {
    val colors = GiftyTheme.colors
    val upcoming = item.upcoming
    val occasion = occasionTitle(upcoming.occasion)
    val details = listOfNotNull(
        occasion.takeIf { upcoming.person != null },
        occurrenceDate(upcoming.date),
        upcoming.turningAge?.let { stringResource(R.string.coming_up_turns, it) },
    ).joinToString(" · ")
    val openLabel = stringResource(
        when (item.target) {
            is ComingUpTarget.Person -> R.string.people_open
            is ComingUpTarget.Occasion -> R.string.people_edit
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = GiftySpacing.m),
        horizontalArrangement = Arrangement.spacedBy(GiftySpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs)) {
            Text(
                text = upcoming.person?.name ?: occasion,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(text = details, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        Text(
            text = whenText(upcoming),
            style = MaterialTheme.typography.labelLarge,
            color = if (upcoming.daysUntil == 0) colors.textPrimary else colors.textSecondary,
        )
    }
}

@Composable
private fun whenText(upcoming: UpcomingOccasion): String = when (val days = upcoming.daysUntil) {
    0 -> stringResource(R.string.coming_up_today)
    1 -> stringResource(R.string.coming_up_tomorrow)
    else -> pluralStringResource(R.plurals.coming_up_in_days, days, days)
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = GiftySpacing.s),
        style = MaterialTheme.typography.bodyMedium,
        color = GiftyTheme.colors.textSecondary,
    )
}
