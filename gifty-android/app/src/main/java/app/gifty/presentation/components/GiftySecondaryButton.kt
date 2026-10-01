package app.gifty.presentation.components

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
import app.gifty.presentation.theme.GiftyShapes
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme

/** An outlined action that sits next to a [GiftyPrimaryButton] without competing with it. */
@Composable
fun GiftySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GiftyTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = GiftyShapes.button,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textMuted,
        ),
        border = BorderStroke(1.dp, colors.border),
        contentPadding = PaddingValues(horizontal = GiftySpacing.xl, vertical = GiftySpacing.m),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
