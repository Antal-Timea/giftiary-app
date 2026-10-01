package app.gifty.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object GiftySpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** Modest radii: Gifty should not read as a stack of rounded cards. */
object GiftyShapes {
    val input = RoundedCornerShape(8.dp)
    val button = RoundedCornerShape(10.dp)
    val container = RoundedCornerShape(12.dp)
    val dialog = RoundedCornerShape(16.dp)
}

// Material components pick these slots: text fields and snackbars use extraSmall, FABs large,
// dialogs extraLarge.
internal val GiftyMaterialShapes = Shapes(
    extraSmall = GiftyShapes.input,
    small = GiftyShapes.button,
    medium = GiftyShapes.container,
    large = GiftyShapes.dialog,
    extraLarge = GiftyShapes.dialog,
)
