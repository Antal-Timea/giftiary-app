package app.giftiary.presentation.gifts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class DetailFactRowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(fontScale: Float = 1f, rows: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GiftiaryTheme { Column(Modifier.padding(horizontal = 24.dp)) { rows() } }
            }
        }
    }

    private fun text(value: String) = compose.onNodeWithText(value, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single().lineCount
    }

    private fun assertInsideScreen(vararg nodes: SemanticsNodeInteraction) {
        val root = compose.onRoot().getBoundsInRoot()
        nodes.forEach {
            val bounds = it.getBoundsInRoot()
            assertTrue("$bounds outside $root", bounds.left >= root.left && bounds.right <= root.right)
        }
    }

    private fun assertNear(expected: Dp, actual: Dp) =
        assertTrue("expected $expected, was $actual", (expected - actual).value in -1f..1f)

    @Test
    fun `at normal size the value sits beside its name, on the right`() {
        show { DetailFactRow("Approximate price") { DetailFactValue("~45,000 Ft") } }

        val label = text("Approximate price").getBoundsInRoot()
        val value = text("~45,000 Ft").getBoundsInRoot()
        assertTrue(value.left > label.right)
        assertNear((label.top + label.bottom) / 2, (value.top + value.bottom) / 2)
        assertNear(compose.onRoot().getBoundsInRoot().right - 24.dp, value.right)
        assertEquals(1, text("Approximate price").lineCount())
    }

    @Test
    fun `a fact value is set at the board's 14,5 bold, quieter than an action`() {
        show { DetailFactRow("Approximate price") { DetailFactValue("~10,000 HUF") } }

        val results = mutableListOf<TextLayoutResult>()
        text("~10,000 HUF").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        val style = results.single().layoutInput.style
        assertEquals(14.5.sp, style.fontSize)
        assertEquals(FontWeight.Bold, style.fontWeight)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h844dp")
    fun `in Hungarian at normal size the long names still stay beside their values`() {
        show {
            DetailFactRow("Hozzávetőleges ár") { DetailFactValue("kb. 45 000 Ft") }
            DetailFactRow("Termék linkje") { DetailLinkValue("https://example.com/headphones") }
        }

        assertTrue(text("kb. 45 000 Ft").getBoundsInRoot().left > text("Hozzávetőleges ár").getBoundsInRoot().right)
        assertTrue(text("Link megnyitása").getBoundsInRoot().left > text("Termék linkje").getBoundsInRoot().right)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h844dp")
    fun `at large text a name that would not fit goes above its value, whole and first, the value still on the right`() {
        show(fontScale = 2f) { DetailFactRow("Hozzávetőleges ár") { DetailFactValue("kb. 45 000 Ft") } }

        val label = text("Hozzávetőleges ár")
        val value = text("kb. 45 000 Ft")
        assertEquals(1, label.lineCount())
        assertTrue("${value.getBoundsInRoot()} vs ${label.getBoundsInRoot()}", value.getBoundsInRoot().top >= label.getBoundsInRoot().bottom)
        assertNear(compose.onRoot().getBoundsInRoot().right - 24.dp, value.getBoundsInRoot().right)
        assertInsideScreen(label, value)
        val read = compose.onNodeWithText("Hozzávetőleges ár").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertEquals(listOf("Hozzávetőleges ár", "kb. 45 000 Ft"), read)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h844dp")
    fun `at large text the product link goes below its name and can still be opened`() {
        show(fontScale = 2f) { DetailFactRow("Termék linkje") { DetailLinkValue("https://example.com/headphones") } }

        val label = text("Termék linkje")
        val link = compose.onNodeWithText("Link megnyitása")
        assertEquals(1, label.lineCount())
        assertTrue("${link.getBoundsInRoot()} vs ${label.getBoundsInRoot()}", link.getBoundsInRoot().top >= label.getBoundsInRoot().bottom)
        assertInsideScreen(label, link)
        link.assertHasClickAction()
        assertEquals("Termék linkje", link.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
    }
}
