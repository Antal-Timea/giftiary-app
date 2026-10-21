package app.giftiary.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the shared list parts and add button tell accessibility services. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListPartsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a row announces the click as its caller describes it, and opens the item`() {
        var opened = 0
        compose.setContent {
            GiftiaryTheme {
                ListRow(title = "Birthday list", detail = "3 wishes", onClickLabel = "Open wishlist", onClick = { opened++ })
            }
        }

        val row = compose.onNodeWithText("Birthday list")
        row.assertHasClickAction()
        val label = row.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label
        assertEquals("Open wishlist", label)
        row.performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `a section heading is a heading`() {
        compose.setContent {
            GiftiaryTheme {
                Column { SectionHeading("Wishes") }
            }
        }

        compose.onNodeWithText("Wishes").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `the add button is labelled by its text and adds`() {
        var added = 0
        compose.setContent {
            GiftiaryTheme {
                GiftiaryAddFab(text = "Add person", onClick = { added++ })
            }
        }

        compose.onNodeWithText("Add person").assertHasClickAction().performClick()
        assertEquals(1, added)
    }
}
