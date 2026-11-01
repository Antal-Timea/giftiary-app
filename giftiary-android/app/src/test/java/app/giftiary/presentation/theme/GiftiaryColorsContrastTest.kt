package app.giftiary.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Guards WCAG AA contrast for the color pairs the screens actually render, so palette tweaks
 * cannot silently make text unreadable.
 *
 * onSupport on support (Material onSecondary on secondary) is deliberately absent: the agreed
 * dark pair is about 1.5:1 and fails WCAG AA for normal text. Its absence here does not mean it
 * is safe; it must not be used for small or normal-size text until the palette is revised.
 */
class GiftiaryColorsContrastTest {

    @Test
    fun `text meets normal-size contrast on the surfaces it is shown on`() {
        forEachTheme { colors ->
            listOf(
                "textPrimary on background" to (colors.textPrimary to colors.background),
                "textPrimary on surfaceElevated" to (colors.textPrimary to colors.surfaceElevated),
                "textPrimary on surfaceContext" to (colors.textPrimary to colors.surfaceContext),
                "textLabel on surfaceContext" to (colors.textLabel to colors.surfaceContext),
                "textPrimary on surfaceAlt" to (colors.textPrimary to colors.surfaceAlt),
                "selected chip text on selected" to (colors.background to colors.selected),
                "navigationUnselected on navigationSurface" to (colors.navigationUnselected to colors.navigationSurface),
                "directionGiven on background" to (colors.directionGiven to colors.background),
                "directionReceived on background" to (colors.directionReceived to colors.background),
                "textSecondary on background" to (colors.textSecondary to colors.background),
                "textSecondary on surfaceElevated" to (colors.textSecondary to colors.surfaceElevated),
                "textMuted on background" to (colors.textMuted to colors.background),
                "textLabel on inputSurface" to (colors.textLabel to colors.inputSurface),
                "error on inputSurface" to (colors.error to colors.inputSurface),
                "error on background" to (colors.error to colors.background),
                "onPrimaryAction on primaryAction" to (colors.onPrimaryAction to colors.primaryAction),
                "primaryActionText on surfaceElevated" to (colors.primaryActionText to colors.surfaceElevated),
            ).forEach { (name, pair) -> assertContrast(name, pair, minimum = 4.5) }
        }
    }

    @Test
    fun `interactive elements are distinguishable from the background`() {
        forEachTheme { colors ->
            assertContrast("primaryAction on background", colors.primaryAction to colors.background, minimum = 3.0)
            assertContrast("inputFocused on inputSurface", colors.inputFocused to colors.inputSurface, minimum = 3.0)
            assertContrast("caret on inputSurface", colors.caret to colors.inputSurface, minimum = 3.0)
        }
    }

    @Test
    fun `the light caret is the action coral, not the teal of the focus border`() {
        assertEquals(LightGiftiaryColors.primaryAction, LightGiftiaryColors.caret)
        assertNotEquals(LightGiftiaryColors.inputFocused, LightGiftiaryColors.caret)
    }

    @Test
    fun `metadata text stays legible though below normal-text contrast`() {
        // DS-01 allows textTertiary only for non-essential metadata at 11.5sp and up.
        forEachTheme { colors ->
            assertContrast("textTertiary on background", colors.textTertiary to colors.background, minimum = 3.5)
        }
    }

    private fun forEachTheme(block: (GiftiaryColors) -> Unit) {
        block(LightGiftiaryColors)
        block(DarkGiftiaryColors)
    }

    private fun assertContrast(name: String, pair: Pair<Color, Color>, minimum: Double) {
        val (foreground, background) = pair
        val lighter = max(foreground.luminance(), background.luminance())
        val darker = min(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05) / (darker + 0.05)
        assertTrue("$name has contrast %.2f, expected at least $minimum".format(ratio), ratio >= minimum)
    }
}
