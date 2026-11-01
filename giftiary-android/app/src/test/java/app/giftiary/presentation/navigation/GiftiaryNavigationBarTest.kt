package app.giftiary.presentation.navigation

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The navigation unit: four destinations, and a global add that is an action, not a fifth tab. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GiftiaryNavigationBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun `the four destinations are tabs and the current one is selected`() {
        compose.setContent {
            GiftiaryTheme {
                GiftiaryNavigationBar(selected = AppSection.PEOPLE, onSelect = {}, onAdd = {})
            }
        }

        listOf("Home", "Gifts", "Lists").forEach { compose.onNodeWithText(it).assert(isTab).assertIsNotSelected() }
        compose.onNodeWithText("People").assert(isTab).assertIsSelected()
    }

    @Test
    fun `a destination outside the sections selects no tab`() {
        compose.setContent {
            GiftiaryTheme {
                GiftiaryNavigationBar(selected = AppSection.SETTINGS, onSelect = {}, onAdd = {})
            }
        }

        listOf("Home", "People", "Gifts", "Lists").forEach { compose.onNodeWithText(it).assertIsNotSelected() }
        compose.onAllNodesWithText("Settings").assertCountEquals(0)
    }

    @Test
    fun `tapping a destination selects its section, Lists being the wishlists`() {
        val selected = mutableListOf<AppSection>()
        compose.setContent {
            GiftiaryTheme {
                GiftiaryNavigationBar(selected = AppSection.HOME, onSelect = { selected += it }, onAdd = {})
            }
        }

        compose.onNodeWithText("Lists").performClick()
        compose.onNodeWithText("Gifts").performClick()
        assertEquals(listOf(AppSection.WISHLISTS, AppSection.GIFTS), selected)
    }

    @Test
    fun `the global add is a labelled button that adds without navigating`() {
        var added = 0
        val selected = mutableListOf<AppSection>()
        compose.setContent {
            GiftiaryTheme {
                GiftiaryNavigationBar(selected = AppSection.HOME, onSelect = { selected += it }, onAdd = { added++ })
            }
        }

        compose.onNodeWithContentDescription("Add a gift, idea, wishlist item or person")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, added)
        assertEquals(emptyList<AppSection>(), selected)
    }
}
