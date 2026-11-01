package app.giftiary.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The approved spacing scale (DS-02): 4, 8, 12, 16, 20, 22, 24, 26, 32. The size names predate
 * it; the design's in-between steps are named by their job.
 */
object GiftiarySpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp

    /** Between optional fields inside a disclosed group. */
    val fieldGroup = 20.dp

    /** Home, People, Gift history, Lists, details, Settings, Profile and sheets. */
    val browseGutter = 22.dp

    val xl = 24.dp

    /** Forms: the extra 2dp keeps a field off the edge of a phone held in one hand. */
    val taskGutter = xl

    /** Between sections within a screen. */
    val section = 26.dp

    val xxl = 32.dp
    val xxxl = 48.dp
}

/** Widths that keep content readable on wide windows; phones stay below them. */
object GiftiaryLayout {
    val readableMaxWidth = 640.dp

    /** Several columns side by side, e.g. wide Home. */
    val wideMaxWidth = 1040.dp

    /** The list beside a detail pane, e.g. People on a wide window. */
    val listPaneWidth = 360.dp
}

/**
 * The approved radii (DS-02), each with one job. Content stays uncontained: there is no general
 * card shape, only the specific surfaces below.
 */
object GiftiaryShapes {
    /** Every text field, selector, search field and the photo tile. */
    val input = RoundedCornerShape(16.dp)

    /** The full-width primary button and the details toggle; nothing else. */
    val button = RoundedCornerShape(18.dp)

    /** A contained supporting surface, and the Coming-up band when inset. */
    val container = RoundedCornerShape(24.dp)
    val dialog = RoundedCornerShape(24.dp)

    /** Bottom sheets and the navigation bar: top corners only. */
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** The full-bleed Coming-up band. */
    val band = RoundedCornerShape(32.dp)

    /** The bottom of an editorial hero. */
    val hero = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)

    /** Avatars, monograms, chips, pill actions, status pills, the back affordance. */
    val pill = CircleShape

    val giftThumbnail = RoundedCornerShape(14.dp)
    val wishThumbnail = RoundedCornerShape(16.dp)
}

// Material components pick these slots: text fields and snackbars use extraSmall, FABs large,
// dialogs extraLarge.
internal val GiftiaryMaterialShapes = Shapes(
    extraSmall = GiftiaryShapes.input,
    small = GiftiaryShapes.button,
    medium = GiftiaryShapes.container,
    large = GiftiaryShapes.container,
    extraLarge = GiftiaryShapes.dialog,
)
