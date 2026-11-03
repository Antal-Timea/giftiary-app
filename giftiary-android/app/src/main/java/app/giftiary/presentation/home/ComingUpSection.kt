package app.giftiary.presentation.home

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.domain.model.OccasionPlan
import app.giftiary.domain.usecase.ObserveUpcomingOccasionsUseCase
import app.giftiary.presentation.components.GiftiaryAvatar
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.occasions.occasionShortDayMonth
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.util.UUID

/**
 * The aqua "Coming up" band of Home and People. [showRelationship] adds each person's relationship
 * to the occasion line, as People does; [footer] closes the band, e.g. with Home's link to the
 * user's own occasions.
 */
@Composable
fun ComingUpBand(
    content: ComingUpContent,
    onOpen: (ComingUpItem) -> Unit,
    modifier: Modifier = Modifier,
    showRelationship: Boolean = false,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    // Until the local read answers there is nothing honest to show, and it answers at once.
    if (content == ComingUpContent.Loading) return
    val colors = GiftiaryTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContext, GiftiaryShapes.band)
            .padding(start = GiftiarySpacing.browseGutter, end = GiftiarySpacing.browseGutter, top = 18.dp, bottom = GiftiarySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
    ) {
        Text(
            text = stringResource(R.string.coming_up_title),
            modifier = Modifier.semantics { heading() },
            style = GiftiaryType.sectionTitle,
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
                    if (index > 0) GiftiaryDivider()
                    ComingUpRow(item = item, showRelationship = showRelationship, onClick = { onOpen(item) })
                }
            }
        }
        footer?.invoke(this)
    }
}

@Composable
private fun ComingUpRow(item: ComingUpItem, showRelationship: Boolean, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    val upcoming = item.upcoming
    val occasion = occasionTitle(upcoming.occasion)
    // "Husband · Birthday Oct 12 · turns 41", as the board sets it: the occasion and its day read as one.
    val date = occasionShortDayMonth(upcoming.date.dayOfMonth, upcoming.date.monthValue)
    val details = listOfNotNull(
        upcoming.person?.relationship?.takeIf { showRelationship && it.isNotBlank() },
        if (upcoming.person != null) "$occasion $date" else date,
        upcoming.turningAge?.let { stringResource(R.string.coming_up_turns, it) },
    ).joinToString(" · ")
    val bought = item.plan?.bought ?: 0
    // Teal once something for the occasion is bought, coral while it is not; the words say which.
    val accent = if (bought > 0) colors.directionReceived else colors.primaryAction
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.people_open), onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = GiftiarySpacing.s)
            // Drawn rather than measured, so the bar follows the row however its text wraps.
            .drawBehind {
                drawRoundRect(accent, size = Size(ACCENT_WIDTH.toPx(), size.height), cornerRadius = CornerRadius(2.dp.toPx()))
            },
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(ACCENT_WIDTH))
        GiftiaryAvatar(name = upcoming.person?.name ?: occasion, size = 40.dp, onContext = true)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = upcoming.person?.name ?: occasion, style = GiftiaryType.actionLarge, color = colors.textPrimary)
            Text(
                text = details,
                modifier = Modifier.padding(top = 1.dp),
                style = GiftiaryType.label.copy(fontWeight = GiftiaryType.body.fontWeight),
                color = colors.textLabel,
            )
            PlanLine(item.plan, modifier = Modifier.padding(top = 5.dp))
        }
        DaysCountdown(upcoming.daysUntil)
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = colors.textLabel,
        )
    }
}

/**
 * "3 ideas  ✓ 1 bought", or the coral "No ideas yet". What is still missing is never claimed: the
 * app only knows the ideas and what of them is bought.
 */
@Composable
fun PlanLine(plan: OccasionPlan?, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    val ideas = plan?.ideas ?: 0
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        if (ideas == 0) {
            PlanStatus(R.drawable.ic_plan_open, stringResource(R.string.coming_up_no_ideas), colors.primaryAction)
            return@FlowRow
        }
        Text(
            text = pluralStringResource(R.plurals.coming_up_ideas, ideas, ideas),
            style = GiftiaryType.metadata.copy(fontWeight = GiftiaryType.contentTitle.fontWeight),
            color = colors.textLabel,
        )
        val bought = plan?.bought ?: 0
        if (bought > 0) {
            PlanStatus(R.drawable.ic_check, pluralStringResource(R.plurals.coming_up_bought, bought, bought), colors.directionReceived)
        }
    }
}

@Composable
private fun PlanStatus(@DrawableRes icon: Int, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(10.dp), tint = color)
        Text(text = text, style = GiftiaryType.metadata.copy(fontWeight = GiftiaryType.rowTitle.fontWeight), color = color)
    }
}

/** The big Lora day count with its unit underneath; "Today" alone when it is today. */
@Composable
fun DaysCountdown(days: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (days == 0) {
            Text(text = stringResource(R.string.coming_up_today), style = GiftiaryType.sectionTitle, color = colors.textPrimary)
            return@Column
        }
        Text(
            text = days.toString(),
            style = if (large) GiftiaryType.number.copy(fontSize = GiftiaryType.number.fontSize * LARGE_NUMBER_SCALE) else GiftiaryType.number,
            color = colors.textPrimary,
        )
        Text(
            text = pluralStringResource(R.plurals.coming_up_days_unit, days).uppercase(),
            modifier = Modifier.padding(top = 2.dp),
            style = GiftiaryType.numberUnit,
            color = colors.textLabel,
        )
    }
}

private val ACCENT_WIDTH = 3.dp

// 34sp on Person detail's occasion card against the 23sp of a row.
private const val LARGE_NUMBER_SCALE = 34f / 23f

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
        style = GiftiaryType.bodySmall,
        color = GiftiaryTheme.colors.textLabel,
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
