package app.giftiary.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/** An outlined action that sits next to a [GiftiaryPrimaryButton] without competing with it. */
@Composable
fun GiftiarySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GiftiaryTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = GiftiaryShapes.button,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textMuted,
        ),
        border = BorderStroke(1.dp, colors.border),
        contentPadding = PaddingValues(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.m),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
