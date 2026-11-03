package app.giftiary.presentation.gifts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
class DetailContextCardTest {
    @get:Rule
    val compose = createComposeRule()

    private var opened = 0

    private fun show(fontScale: Float = 1f, card: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GiftiaryTheme { Column(Modifier.padding(horizontal = 24.dp)) { card() } }
            }
        }
    }

    @Composable
    private fun IdeaCard(title: String, supporting: String, status: String) = DetailContextCard(
        name = "Peter",
        title = title,
        supporting = supporting,
        onClickLabel = "Open person",
        onClick = { opened++ },
        trailing = { Text(status) },
    )

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
    fun `at normal size the status sits beside the text, before the chevron`() {
        show { IdeaCard("For Peter", "Birthday · October 12", "Purchased") }

        val title = text("For Peter").getBoundsInRoot()
        val supporting = text("Birthday · October 12").getBoundsInRoot()
        val status = text("Purchased").getBoundsInRoot()
        assertTrue(status.left > title.right && status.left > supporting.right)
        assertNear((title.top + supporting.bottom) / 2, (status.top + status.bottom) / 2)
        assertEquals(1, text("Birthday · October 12").lineCount())
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h844dp")
    fun `in Hungarian at large text the status moves below the context, which stays readable`() {
        show(fontScale = 2f) { IdeaCard("Neki: Peter", "Születésnap · október 12.", "Megvásárolva") }

        val title = text("Neki: Peter")
        val supporting = text("Születésnap · október 12.")
        val status = text("Megvásárolva")
        assertEquals(1, title.lineCount())
        assertTrue(supporting.lineCount() <= 2)
        assertEquals(1, status.lineCount())
        assertTrue(status.getBoundsInRoot().top >= supporting.getBoundsInRoot().bottom)
        assertNear(title.getBoundsInRoot().left, status.getBoundsInRoot().left)
        assertInsideScreen(title, supporting, status)
    }

    @Test
    fun `in English at large text the status also moves below, and words stay whole`() {
        show(fontScale = 2f) { IdeaCard("For Peter", "Birthday · October 12", "Purchased") }

        val supporting = text("Birthday · October 12")
        val status = text("Purchased")
        assertEquals(1, text("For Peter").lineCount())
        assertTrue(supporting.lineCount() <= 2)
        assertTrue(status.getBoundsInRoot().top >= supporting.getBoundsInRoot().bottom)
        assertInsideScreen(text("For Peter"), supporting, status)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h844dp")
    fun `the card stays one action that reads its context, then the status`() {
        show(fontScale = 2f) { IdeaCard("Neki: Peter", "Születésnap · október 12.", "Megvásárolva") }

        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        val card = compose.onNode(hasClickAction())
        assertEquals("Open person", card.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        val read = card.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Neki: Peter", "Születésnap · október 12.", "Megvásárolva"), read)
        assertTrue(card.getBoundsInRoot().let { it.bottom - it.top } >= 48.dp)
        card.performClick()
        assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h844dp")
    fun `without a status the context keeps the whole width at large text`() {
        show(fontScale = 2f) { DetailContextCard(name = "Peter’s wishlist", title = "Ezen a listán: Peter’s wishlist") }

        val title = text("Ezen a listán: Peter’s wishlist")
        assertTrue(title.lineCount() <= 2)
        assertInsideScreen(title)
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.onAllNodesWithText("Ezen a listán", substring = true).assertCountEquals(1)
    }
}
