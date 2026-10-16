package app.giftiary.presentation.home

import android.content.res.Resources
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.model.OccasionPlan
import app.giftiary.domain.usecase.ObserveUpcomingOccasionsUseCase
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.occasions.occurrenceDate
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import java.util.UUID

@Composable
fun ComingUpSection(
    content: ComingUpContent,
    onOpen: (occasionId: UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Until the local read answers there is nothing honest to show, and it answers at once.
    if (content == ComingUpContent.Loading) return
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.coming_up_title),
            modifier = Modifier
                .padding(bottom = GiftiarySpacing.s)
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
                    ComingUpRow(item = item, onClick = { onOpen(item.occasionId) })
                }
            }
        }
    }
}

@Composable
private fun ComingUpRow(item: ComingUpItem, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    val upcoming = item.upcoming
    val occasion = occasionTitle(upcoming.occasion)
    val details = listOfNotNull(
        occasion.takeIf { upcoming.person != null },
        occurrenceDate(upcoming.date),
        upcoming.turningAge?.let { stringResource(R.string.coming_up_turns, it) },
        occasionPlanText(LocalResources.current, item.plan),
    ).joinToString(" · ")
    val openLabel = stringResource(R.string.people_open)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = GiftiarySpacing.m),
        horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs)) {
            Text(
                text = upcoming.person?.name ?: occasion,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(text = details, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        Text(
            text = daysUntilText(upcoming.daysUntil),
            style = MaterialTheme.typography.labelLarge,
            color = if (upcoming.daysUntil == 0) colors.textPrimary else colors.textSecondary,
        )
    }
}

/** "Today", "Tomorrow" or "In N days". */
@Composable
fun daysUntilText(days: Int): String = when (days) {
    0 -> stringResource(R.string.coming_up_today)
    1 -> stringResource(R.string.coming_up_tomorrow)
    else -> pluralStringResource(R.plurals.coming_up_in_days, days, days)
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = GiftiarySpacing.s),
        style = MaterialTheme.typography.bodyMedium,
        color = GiftiaryTheme.colors.textSecondary,
    )
}

/**
 * "2 ideas · 1 bought", or null when nothing is planned. Bought is left out while there is none;
 * nothing is said about what is missing, since Giftiary cannot know it.
 */
fun occasionPlanText(resources: Resources, plan: OccasionPlan?): String? {
    if (plan == null || plan.ideas == 0) return null
    return listOfNotNull(
        resources.getQuantityString(R.plurals.coming_up_ideas, plan.ideas, plan.ideas),
        plan.bought.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.coming_up_bought, it, it) },
    ).joinToString(" · ")
}
