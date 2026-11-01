package app.giftiary.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.giftiary.domain.model.GiftDirection
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the design-system foundation components show and tell accessibility services. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoundationComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a text field is announced with the label above it, and its error with it`() {
        compose.setContent {
            GiftiaryTheme {
                GiftiaryTextField(value = "", onValueChange = {}, label = "Gift idea", errorMessage = "A gift idea needs a name.")
            }
        }

        compose.onNode(hasSetTextAction() and hasText("Gift idea"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "A gift idea needs a name."))
        compose.onNodeWithText("A gift idea needs a name.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the search field offers a clear control only while it holds a query`() {
        compose.setContent {
            GiftiaryTheme {
                var query by remember { mutableStateOf("") }
                GiftiarySearchField(query = query, onQueryChange = { query = it }, placeholder = "Search people…")
            }
        }

        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("Robert")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }

    @Test
    fun `a filter chip reports its selection and selects on tap`() {
        var tapped = 0
        compose.setContent {
            GiftiaryTheme {
                Row {
                    GiftiaryFilterChip(text = "All", selected = true, onClick = {})
                    GiftiaryFilterChip(text = "Given", selected = false, onClick = { tapped++ }, direction = GiftDirection.GIVEN)
                }
            }
        }

        compose.onNodeWithText("All").assertIsSelected()
        compose.onNodeWithText("Given").assertIsNotSelected().performClick()
        assertEquals(1, tapped)
    }

    @Test
    fun `a monogram is decorative and a photo replaces it`() {
        compose.setContent {
            GiftiaryTheme {
                Column {
                    GiftiaryAvatar(name = "anna")
                    GiftiaryAvatar(name = "Peter", photo = { Box(Modifier.semantics { contentDescription = "Peter" }) })
                }
            }
        }

        compose.onNodeWithText("A").assertDoesNotExist()
        compose.onNodeWithText("P").assertDoesNotExist()
        compose.onNodeWithContentDescription("Peter").assertExists()
    }

    @Test
    fun `a monogram uses the first character of the name, whatever its script`() {
        assertEquals("É", monogramInitial("éva"))
        assertEquals("Ő", monogramInitial("  Ődön"))
        assertEquals("🎁", monogramInitial("🎁 gift club"))
        assertEquals("", monogramInitial("   "))
    }

    @Test
    fun `the offline line names what is waiting only when something is`() {
        compose.setContent {
            GiftiaryTheme {
                Column {
                    OfflineStatus(waitingChanges = 0)
                    OfflineStatus(waitingChanges = 2)
                }
            }
        }

        compose.onNodeWithText("Offline").assertExists()
        compose.onNodeWithText("Offline · 2 changes waiting to sync").assertExists()
    }

    @Test
    fun `pending sync says the save succeeded, and a failed refresh offers a retry`() {
        var retried = 0
        compose.setContent {
            GiftiaryTheme {
                Column {
                    PendingSyncStatus()
                    RefreshFailedLine(onRetry = { retried++ })
                }
            }
        }

        compose.onNodeWithText("Waiting to sync · Saved on this device").assertExists()
        compose.onNodeWithText("Couldn’t refresh · Showing saved data").assertExists()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `a saving primary action ignores taps`() {
        compose.setContent {
            GiftiaryTheme {
                GiftiaryPrimaryButton(text = "Save gift", onClick = {}, loading = true, trailingArrow = true)
            }
        }

        compose.onNodeWithText("Save gift").assertIsNotEnabled()
    }
}
