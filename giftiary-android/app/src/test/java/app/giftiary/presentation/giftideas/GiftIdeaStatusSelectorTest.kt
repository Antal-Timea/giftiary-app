package app.giftiary.presentation.giftideas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.WithFontScale
import app.giftiary.testing.assertNoClippedText
import app.giftiary.testing.lineCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The status choice in the idea editor. Text widths only come out real with native graphics; 312dp
 * is a 360dp phone less the editor's gutters.
 */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GiftIdeaStatusSelectorTest {

    @get:Rule
    val compose = createComposeRule()

    private var selected by mutableStateOf(GiftIdeaStatus.IDEA)

    private fun show(fontScale: Float = 1f, width: Dp = 312.dp) {
        compose.setContent {
            GiftiaryTheme {
                WithFontScale(fontScale) {
                    Box(Modifier.width(width).testTag(SELECTOR)) {
                        GiftIdeaStatusSelector(selected = selected, onSelect = { selected = it })
                    }
                }
            }
        }
    }

    private fun bounds(label: String): DpRect = compose.onNodeWithText(label).getBoundsInRoot()

    /** Every label whole on one line, inside the control, and clear of the others. */
    private fun assertLabelsWhole(labels: List<String>) {
        val selector = compose.onNodeWithTag(SELECTOR).getBoundsInRoot()
        labels.forEach { label ->
            assertEquals("$label on one line", 1, compose.onNodeWithText(label, useUnmergedTree = true).lineCount())
            val box = bounds(label)
            assertTrue("$label inside the control", box.left >= selector.left && box.right <= selector.right)
        }
        labels.forEachIndexed { i, a ->
            labels.drop(i + 1).forEach { b ->
                val x = bounds(a)
                val y = bounds(b)
                val apart = x.right <= y.left || y.right <= x.left || x.bottom <= y.top || y.bottom <= x.top
                assertTrue("$a and $b do not overlap", apart)
            }
        }
        compose.onNodeWithTag(SELECTOR).assertNoClippedText()
    }

    private fun sideBySide(labels: List<String>) = labels.map { bounds(it).top }.distinct().size == 1

    @Test
    fun `English labels sit side by side on a phone`() {
        show()

        assertLabelsWhole(ENGLISH)
        assertTrue(sideBySide(ENGLISH))
    }

    @Test
    @Config(qualifiers = "hu")
    fun `Hungarian labels that do not fit their third are stacked, never broken or overlapping`() {
        show()

        assertLabelsWhole(HUNGARIAN)
        assertTrue("stacked", HUNGARIAN.zipWithNext().all { (a, b) -> bounds(a).bottom <= bounds(b).top })
    }

    @Test
    fun `at large text the English labels stack too`() {
        show(fontScale = 2f)

        assertLabelsWhole(ENGLISH)
        assertTrue(ENGLISH.zipWithNext().all { (a, b) -> bounds(a).bottom <= bounds(b).top })
    }

    @Test
    @Config(qualifiers = "hu")
    fun `Hungarian at large text stays whole`() {
        show(fontScale = 2f)

        assertLabelsWhole(HUNGARIAN)
    }

    @Test
    @Config(qualifiers = "hu-w600dp-h800dp")
    fun `with room Hungarian labels sit side by side too`() {
        show(width = 552.dp)

        assertLabelsWhole(HUNGARIAN)
        assertTrue(sideBySide(HUNGARIAN))
    }

    @Test
    fun `each status is a radio choice that can be picked`() {
        show()

        compose.onNodeWithText("Purchased").performClick()

        assertEquals(GiftIdeaStatus.PURCHASED, selected)
        compose.onNodeWithText("Purchased")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    private companion object {
        const val SELECTOR = "selector"
        val ENGLISH = listOf("Idea", "Planned", "Purchased")
        val HUNGARIAN = listOf("Ötlet", "Tervezett", "Megvásárolva")
    }
}
