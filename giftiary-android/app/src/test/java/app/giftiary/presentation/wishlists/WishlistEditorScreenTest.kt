package app.giftiary.presentation.wishlists

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Create and Edit list as a task flow: name, who it is for, its privacy, and one Save. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1400dp")
class WishlistEditorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val martin = person("Martin")
    private val creating = WishlistEditorUiState(
        isNew = true,
        status = WishlistEditorStatus.EDITING,
        activePeople = listOf(martin),
        peopleLoaded = true,
    )
    private val editing = WishlistEditorUiState(
        isNew = false,
        status = WishlistEditorStatus.EDITING,
        name = "Martin’s birthday wishes",
        savedName = "Martin’s birthday wishes",
        owner = WishlistOwner.Person("Martin"),
    )

    private val events = mutableListOf<String>()
    private var state by mutableStateOf(creating)
    private var shown = false

    private val actions = WishlistEditorActions(
        onBack = { events += "back" },
        onNameChange = { events += "name $it" },
        onPersonChange = { events += "for ${it?.name}" },
        onSave = { events += "save" },
        onWriteErrorShown = { events += "error shown" },
        onDeleteRequested = { events += "delete" },
        onDeleteDismissed = { events += "dismiss" },
        onDeleteConfirmed = { events += "confirm" },
    )

    private fun show(state: WishlistEditorUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent { GiftiaryTheme { WishlistEditorScreen(state = this.state, actions = actions) } }
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    private fun assertTopToBottom(texts: List<String>) {
        val tops = texts.map(::top)
        assertEquals("top to bottom: $texts", tops.sorted(), tops)
    }

    @Test
    fun `creating reads header, name, who it is for, privacy, then create`() {
        show(creating)

        assertTopToBottom(
            listOf(
                "GIFTIARY", "Create a list", "A place to collect wishes — yours, or someone else’s.", "List name", "List for",
                "Yourself or anyone you keep in Giftiary — they don’t need an account. This can’t be changed later.",
                "Only you", "New lists stay private. You can share this one whenever you like.", "Create list",
            ),
        )
        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
        compose.onNodeWithText("Delete", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Add photo").assertDoesNotExist()
    }

    @Test
    fun `a list kept for someone says it stays private`() {
        show(creating.copy(personId = martin.id))

        compose.onNodeWithText("Martin").assertExists()
        compose.onNodeWithText("Lists you keep for someone always stay private to you.").assertExists()
    }

    @Test
    fun `typing, creating and going back reach the editor`() {
        show(creating)

        compose.onNodeWithText("List name").performTextInput("Christmas")
        compose.onNodeWithText("Create list").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("name Christmas", "save", "back"), events.filterNot { it == "name " })
    }

    @Test
    fun `editing keeps the values, shows who it is for without letting it change, and drops the privacy note`() {
        show(editing)

        compose.onNodeWithText("Edit list").assertExists()
        compose.onNodeWithText("Martin’s birthday wishes").assertExists()
        compose.onNodeWithText("Martin").assertExists()
        compose.onNodeWithText("Who a list is for can’t be changed.").assertExists()
        compose.onNodeWithText("Only you").assertDoesNotExist()
        compose.onNodeWithText("Save list").assertExists()
    }

    @Test
    fun `editing keeps delete in the header's options, not beside Save`() {
        show(editing)

        compose.onNodeWithText("Delete", substring = true).assertDoesNotExist()
        val more = compose.onNodeWithContentDescription("More options").getBoundsInRoot()
        assertTrue("the options sit in the header, above the title", more.bottom <= top("Edit list"))

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete wishlist").performClick()
        assertEquals(listOf("delete"), events)
    }

    @Test
    fun `editing offers no cover or photo, as lists have none`() {
        show(editing)

        compose.onAllNodesWithText("cover", substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText("photo", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun `while an edit is saved, Save and Delete cannot be pressed again`() {
        show(editing.copy(isSaving = true))

        compose.onNode(hasText("Save list") and hasClickAction()).assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete wishlist").assertIsNotEnabled().performClick()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `the confirmation names the list and its wishes, and cancel and delete reach the editor`() {
        show(editing.copy(name = "Renamed", confirmingDelete = true, deleteWishCount = 2))

        compose.onNodeWithText("Delete Martin’s birthday wishes?").assertExists()
        compose.onNodeWithText("Its 2 wishes will be deleted too.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("dismiss", "confirm"), events)
    }

    @Test
    fun `a failed delete is said and the form stays`() {
        show(editing.copy(writeError = WishlistWriteError.DELETE_FAILED))

        compose.onNodeWithText("Could not delete. Please try again.").assertExists()
        compose.onNodeWithText("Save list").assertExists()
        compose.mainClock.advanceTimeBy(10_000)
        assertTrue("the error is marked shown", "error shown" in events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h1400dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme { WishlistEditorScreen(state = creating, actions = actions) }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Kinek a listája", "Az új listákat csak te látod. Ezt bármikor megoszthatod.").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }
}
