package app.giftiary.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/**
 * The top of a task flow such as Add a gift: the aqua back circle, the wordmark, the one big
 * title and a standfirst. It is part of the page and scrolls with the form, as on the boards.
 */
@Composable
fun GiftiaryTaskHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // Secondary actions, such as deleting what is edited, kept beside the back circle and away from Save.
    menu: (@Composable () -> Unit)? = null,
    // Share & Invite drops the wordmark and names the list it shares between title and standfirst.
    wordmark: Boolean = true,
    context: (@Composable () -> Unit)? = null,
) {
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GiftiaryBackButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
            menu?.invoke()
        }
        if (wordmark) {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                modifier = Modifier.padding(top = GiftiarySpacing.browseGutter),
                style = GiftiaryType.wordmark,
                color = colors.textLabel,
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .padding(top = if (wordmark) GiftiarySpacing.s else GiftiarySpacing.browseGutter)
                .semantics { heading() },
            style = GiftiaryType.display,
            color = colors.textPrimary,
        )
        if (context != null) Box(Modifier.padding(top = GiftiarySpacing.s)) { context() }
        Text(
            text = subtitle,
            modifier = Modifier.padding(top = GiftiarySpacing.s),
            style = GiftiaryType.body,
            color = colors.textSecondary,
        )
    }
}

/**
 * A task flow with nothing to edit: still loading ([message] null), or gone or unreadable. The
 * back circle stays where the header would put it.
 */
@Composable
fun GiftiaryTaskStatus(message: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        GiftiaryBackButton(
            onClick = onBack,
            modifier = Modifier.padding(start = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter),
        )
        val content = Modifier.weight(1f)
        if (message == null) LoadingContent(content) else MessageContent(message, content)
    }
}

/**
 * One of a few mutually exclusive answers in a task flow, such as "I gave" / "I received". The
 * chosen pill is aqua with a teal outline, so the choice never rests on colour alone; selection
 * stays teal because coral is kept for creating. Put the pills in a `selectableGroup()` row.
 */
@Composable
fun GiftiaryChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Decorative, as the text names the choice, e.g. a direction arrow.
    leadingIcon: Painter? = null,
) {
    val colors = GiftiaryTheme.colors
    val shape = GiftiaryShapes.pill
    val border = if (selected) BorderStroke(2.dp, colors.selected) else BorderStroke(1.4.dp, colors.borderStrong)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .then(if (selected) Modifier.background(colors.surfaceContext, shape) else Modifier)
            .border(border, shape)
            .padding(ChoicePadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (enabled) colors.textPrimary else colors.textMuted
        if (leadingIcon != null) {
            Icon(painter = leadingIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = content)
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            style = GiftiaryType.action.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = content,
        )
    }
}

private val ChoicePadding = PaddingValues(horizontal = 10.dp, vertical = 13.dp)

/**
 * The dashed coral outline of an optional "add" affordance in a form, such as the photo tile or a
 * new person: creating stays coral, and the dashes say nothing is there yet.
 */
fun Modifier.giftiaryDashedOutline(color: Color, cornerRadius: Dp): Modifier = drawBehind {
    val stroke = 1.6.dp.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx() - inset),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
    )
}

