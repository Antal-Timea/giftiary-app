package app.giftiary.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.Lora
import app.giftiary.presentation.theme.PublicSans

/**
 * A person's round avatar: their [photo] when there is one, otherwise the aqua monogram, which is
 * content in its own right rather than an empty slot. [onContext] is for avatars inside the aqua
 * band, where the usual fill would disappear. The monogram is decorative, as the name is always
 * written next to it; a photo slot describes itself.
 */
@Composable
fun GiftiaryAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onContext: Boolean = false,
    photo: (@Composable () -> Unit)? = null,
) {
    val colors = GiftiaryTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(GiftiaryShapes.pill)
            .background(if (onContext) colors.surfaceContextQuiet else colors.avatarFallback),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            photo()
        } else {
            Text(
                text = monogramInitial(name),
                modifier = Modifier.clearAndSetSemantics {},
                style = monogramStyle(size, onContext),
                color = colors.textPrimary,
            )
        }
    }
}

internal fun monogramInitial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).uppercase()
}

// The approved boards set the monogram in Lora inside the aqua band and on large identity
// avatars, and in Public Sans on ordinary rows.
private fun monogramStyle(size: Dp, onContext: Boolean): TextStyle {
    val fontSize = when {
        size >= 64.dp -> 32.sp
        size >= 44.dp -> 16.sp
        size >= 36.dp -> 15.sp
        else -> 11.5.sp
    }
    val family = if (onContext || size >= 64.dp) Lora else PublicSans
    return TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = fontSize)
}

/**
 * A gift, idea or wish thumbnail: the [photo] cropped into the tile, or the branded gift glyph on
 * an aqua tile, never a broken-image cue. 52dp at radius 14 in gift rows; wishes use 64dp at
 * [GiftiaryShapes.wishThumbnail].
 */
@Composable
fun GiftThumbnail(
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    shape: Shape = GiftiaryShapes.giftThumbnail,
    photo: (@Composable () -> Unit)? = null,
) {
    val colors = GiftiaryTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.thumbnailFallback),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            photo()
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_gift_glyph),
                contentDescription = null,
                modifier = Modifier.size(size * GLYPH_RATIO),
                tint = colors.textLabel,
            )
        }
    }
}

private const val GLYPH_RATIO = 22f / 52f

/**
 * The full-bleed editorial hero with a 32dp bottom radius. Until the [image] loads, or while no
 * approved photography exists, the warm yellow fallback stands in for it; [content] is set
 * directly on top. The image fills whatever height the caller or the content gives the hero.
 */
@Composable
fun EditorialHero(
    modifier: Modifier = Modifier,
    image: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(GiftiaryShapes.hero)
            .background(GiftiaryTheme.colors.heroFallback),
    ) {
        if (image != null) {
            Box(Modifier.matchParentSize()) { image() }
        }
        content()
    }
}
