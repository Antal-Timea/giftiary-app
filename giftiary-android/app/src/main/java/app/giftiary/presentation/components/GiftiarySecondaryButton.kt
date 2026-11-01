package app.giftiary.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/** A restrained outlined action next to a coral one: never coral, never filled. */
@Composable
fun GiftiarySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Shown in its own colours, e.g. a sign-in provider's mark; decorative, as the text names it.
    leadingIcon: Painter? = null,
) {
    val colors = GiftiaryTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = GiftiaryShapes.pill,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textMuted,
        ),
        border = BorderStroke(1.5.dp, colors.borderStrong),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = GiftiarySpacing.m),
    ) {
        if (leadingIcon != null) {
            Image(painter = leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(GiftiarySpacing.m))
        }
        Text(text = text, style = GiftiaryType.actionSmall)
    }
}

/**
 * A full-width aqua action that supports the form rather than finishing it, such as
 * "Add more details (optional)". [trailingIcon] is decorative, e.g. a disclosure chevron.
 */
@Composable
fun GiftiarySupportButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: Painter? = null,
) {
    val colors = GiftiaryTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = GiftiaryShapes.button,
        colors = ButtonDefaults.buttonColors(containerColor = colors.supportSurface, contentColor = colors.textPrimary),
        elevation = null,
        contentPadding = PaddingValues(GiftiarySpacing.l),
    ) {
        Text(text = text, style = GiftiaryType.body.copy(fontWeight = FontWeight.Bold))
        if (trailingIcon != null) {
            Spacer(Modifier.width(7.dp))
            Icon(painter = trailingIcon, contentDescription = null, modifier = Modifier.size(13.dp))
        }
    }
}

/**
 * Cancel, Try again, Undo, Go back: the quietest action, still a 48dp target. [underline] marks
 * one standing alone in body text, such as a state's "Clear search".
 */
@Composable
fun GiftiaryTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, underline: Boolean = false) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.textPrimary),
        contentPadding = PaddingValues(horizontal = GiftiarySpacing.xs),
    ) {
        Text(
            text = text,
            style = if (underline) GiftiaryType.actionSmall.copy(textDecoration = TextDecoration.Underline) else GiftiaryType.actionSmall,
        )
    }
}

/** The one back control of pushed screens and task flows: a 48dp aqua circle. */
@Composable
fun GiftiaryBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = colors.supportSurface, contentColor = colors.textPrimary),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.navigate_back),
            modifier = Modifier.size(20.dp),
        )
    }
}
