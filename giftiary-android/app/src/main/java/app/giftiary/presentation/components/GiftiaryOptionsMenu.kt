package app.giftiary.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/** Secondary actions, such as a list's or an editor's, behind one quiet 48dp button. */
@Composable
internal fun OptionsMenu(
    @DrawableRes icon: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    items: @Composable OptionsMenuScope.() -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = GiftiaryTheme.colors
    Box(modifier = modifier) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = colors.textLabel),
        ) {
            Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = GiftiaryShapes.photoCard,
            containerColor = colors.surfaceElevated,
        ) {
            OptionsMenuScope { open = false }.items()
        }
    }
}

internal class OptionsMenuScope(private val close: () -> Unit) {

    /** [quiet] for a destructive action, kept below the others. */
    @Composable
    fun Option(text: String, @DrawableRes icon: Int, onClick: () -> Unit, enabled: Boolean = true, quiet: Boolean = false) {
        val colors = GiftiaryTheme.colors
        val color = if (quiet) colors.textMuted else colors.textPrimary
        if (quiet) GiftiaryDivider()
        DropdownMenuItem(
            text = { Text(text, style = GiftiaryType.actionSmall, color = color) },
            onClick = {
                close()
                onClick()
            },
            enabled = enabled,
            leadingIcon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = color) },
        )
    }
}
