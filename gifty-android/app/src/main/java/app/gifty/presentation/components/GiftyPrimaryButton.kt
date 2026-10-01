package app.gifty.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gifty.presentation.theme.GiftyShapes
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme

/**
 * The one coral action on a screen. While [loading] it ignores taps but keeps its color, so
 * an in-progress save does not look like an unavailable action.
 */
@Composable
fun GiftyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = GiftyTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled && !loading,
        shape = GiftyShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryAction,
            contentColor = colors.onPrimaryAction,
            disabledContainerColor = if (loading) colors.primaryAction else colors.skeleton,
            disabledContentColor = if (loading) colors.onPrimaryAction else colors.textMuted,
        ),
        contentPadding = PaddingValues(horizontal = GiftySpacing.xl, vertical = GiftySpacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(GiftySpacing.s))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
