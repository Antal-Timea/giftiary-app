package app.giftiary.presentation.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import app.giftiary.testing.assertNear
import app.giftiary.testing.lineCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Text with an action at its end keeps its words whole: the action moves under it when they would not fit. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class TrailingActionRowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(width: Dp, text: String) {
        compose.setContent {
            GiftiaryTheme {
                TrailingActionRow(
                    modifier = Modifier.width(width),
                    action = { GiftiaryTextAction(text = "Manage", onClick = {}) },
                ) {
                    Text(text, style = GiftiaryType.sectionTitle)
                }
            }
        }
    }

    @Test
    fun `with room the action sits at the end of the same line`() {
        show(360.dp, "Wishes")

        val text = compose.onNodeWithText("Wishes").getBoundsInRoot()
        val action = compose.onNodeWithText("Manage").getBoundsInRoot()
        assertNear(360.dp, action.right, "action end")
        assertTrue("same line: $text, $action", action.top < text.bottom && text.top < action.bottom)
    }

    @Test
    fun `when a word would not fit beside it, the action moves under the text, at its start`() {
        show(150.dp, "Kívánságlisták")

        val text = compose.onNodeWithText("Kívánságlisták")
        assertEquals(1, text.lineCount())
        val action = compose.onNodeWithText("Manage").getBoundsInRoot()
        assertTrue("action under the text", action.top >= text.getBoundsInRoot().bottom)
        assertTrue("action at the start", action.left < 20.dp)
    }

    @Test
    fun `the text is read before its action, wherever the action is placed`() {
        show(150.dp, "Kívánságlisták")

        val order = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.let { order += it.joinToString() }
            node.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertEquals(listOf("Kívánságlisták", "Manage"), order)
    }
}
