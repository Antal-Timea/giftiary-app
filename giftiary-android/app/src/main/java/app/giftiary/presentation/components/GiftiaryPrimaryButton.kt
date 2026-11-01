package app.giftiary.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/**
 * The one coral action on a screen, full width at the end of a form. While [loading] it ignores
 * taps but keeps its color, so an in-progress save does not look like an unavailable action.
 * [trailingArrow] marks forward movement, as on "Save gift →".
 */
@Composable
fun GiftiaryPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingArrow: Boolean = false,
) {
    CoralButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        shape = GiftiaryShapes.button,
        glow = 6.dp,
        contentPadding = PaddingValues(GiftiarySpacing.l),
    ) {
        Text(text = text, style = GiftiaryType.actionLarge)
        if (trailingArrow) {
            Spacer(Modifier.width(GiftiarySpacing.s))
            Icon(painter = painterResource(R.drawable.ic_arrow_right), contentDescription = null, modifier = Modifier.size(15.dp))
        }
    }
}

/**
 * The primary action where there is no form to close: an empty state's next step, an error's
 * retry, "Plan gift" on an occasion.
 */
@Composable
fun GiftiaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
) {
    CoralButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = false,
        shape = GiftiaryShapes.pill,
        glow = 5.dp,
        contentPadding = PaddingValues(horizontal = GiftiarySpacing.browseGutter, vertical = 13.dp),
    ) {
        if (leadingIcon != null) {
            Icon(painter = leadingIcon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(GiftiarySpacing.s))
        }
        Text(text = text, style = GiftiaryType.action)
    }
}

@Composable
private fun CoralButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean,
    shape: Shape,
    glow: Dp,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    val colors = GiftiaryTheme.colors
    val active = enabled || loading
    Button(
        onClick = onClick,
        // The coral glow is one of the system's three shadows; a disabled action loses it.
        modifier = modifier
            .heightIn(min = 48.dp)
            .shadow(
                elevation = if (active) glow else 0.dp,
                shape = shape,
                ambientColor = colors.primaryAction,
                spotColor = colors.primaryAction,
            ),
        enabled = enabled && !loading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryAction,
            contentColor = colors.onPrimaryAction,
            disabledContainerColor = if (loading) colors.primaryAction else colors.primaryAction.copy(alpha = 0.38f),
            disabledContentColor = if (loading) colors.onPrimaryAction else colors.onPrimaryAction.copy(alpha = 0.7f),
        ),
        elevation = null,
        contentPadding = contentPadding,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(GiftiarySpacing.s))
            }
            content()
        }
    }
}
