package app.giftiary.presentation.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Hungarian word can be wider than a phone at large text ("kívánságlistához"). Every role may then
 * break it at a syllable with a hyphen, the way Android hyphenates the language, rather than
 * splitting it anywhere. Text that fits is laid out exactly as before.
 */
class GiftiaryTypographyTest {

    @Test
    fun `every Giftiary type role hyphenates a word that cannot fit`() {
        val roles = GiftiaryType::class.java.declaredMethods
            .filter { it.returnType == TextStyle::class.java && it.parameterCount == 0 }
            .associate { it.name to it.invoke(GiftiaryType) as TextStyle }
        assertTrue("found the roles", roles.size >= 20)
        roles.forEach { (name, style) -> assertEquals(name, Hyphens.Auto, style.hyphens) }
    }

    @Test
    fun `the Material slots that unstyled components fall back to hyphenate too`() {
        with(GiftiaryTypography) {
            listOf(
                displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
                titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall,
            ).forEach { assertEquals(Hyphens.Auto, it.hyphens) }
        }
    }
}
