package app.giftiary.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiaryTheme

/**
 * A 36dp ivory circle on a hero (back, edit, more) inside a 48dp target. On a photograph the
 * circle is slightly see-through, [containerColor], as the approved boards draw it.
 */
@Composable
fun HeroCircleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = GiftiaryTheme.colors.background,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = GiftiaryTheme.colors.textPrimary),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(containerColor, GiftiaryShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(18.dp))
        }
    }
}
