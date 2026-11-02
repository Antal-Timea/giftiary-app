package app.giftiary.presentation.wishlists

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import app.giftiary.presentation.gifts.PriceError
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Add and Edit wish: the list, what is wished for, then the optional details, as on the board. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h3000dp")
class WishEditorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val adding = WishEditorUiState(isNew = true, status = WishEditorStatus.EDITING, wishlistName = "Peter’s wishlist")
    private val editing = adding.copy(
        isNew = false,
        name = "Wireless headphones",
        savedName = "Wireless headphones",
        note = "Over-ear",
        price = "45000",
        currencyCode = "HUF",
        productUrl = "https://example.org/h",
    )

    private val events = mutableListOf<String>()
    private var state by mutableStateOf(adding)
    private var shown = false

    private val actions = WishEditorActions(
        onBack = { events += "back" },
        onNameChange = { events += "name $it" },
        onPriceChange = { events += "price $it" },
        onProductUrlChange = { events += "url $it" },
        onNoteChange = { events += "note $it" },
        onSave = { events += "save" },
        onSaveErrorShown = {},
        onDeleteRequested = { events += "delete" },
        onDeleteDismissed = { events += "dismiss" },
        onDeleteConfirmed = { events += "confirm" },
        onDeleteErrorShown = { events += "delete error shown" },
    )

    private fun show(state: WishEditorUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent { GiftiaryTheme { WishEditorScreen(state = this.state, actions = actions) } }
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    private fun assertTopToBottom(texts: List<String>) {
        val tops = texts.map(::top)
        assertEquals("top to bottom: $texts", tops.sorted(), tops)
    }

    @Test
    fun `adding reads header, list, what, the details toggle, then save`() {
        show(adding)

        assertTopToBottom(
            listOf(
                "Add a wish", "Save an idea for later.", "Wishlist", "Peter’s wishlist", "What would you like?",
                "That’s all you need — everything else is optional.", "Add more details (optional)", "Save wish",
            ),
        )
        compose.onNodeWithText("Approximate price", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    fun `the details keep every wish field in the board's order, without a photo a wish cannot take`() {
        show(adding)

        compose.onNodeWithText("Add more details (optional)").performClick()
        assertTopToBottom(listOf("Fewer details", "Note", "Approximate price", "A rough amount is enough.", "Product link", "Save wish"))
        compose.onNodeWithText("Photo", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Add photo").assertDoesNotExist()
    }

    @Test
    fun `editing opens with its values and details shown`() {
        show(editing)

        compose.onNodeWithText("Edit wish").assertExists()
        listOf("Peter’s wishlist", "Wireless headphones", "Over-ear", "45000", "https://example.org/h").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("HUF", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `fields and save reach the editor`() {
        show(editing)

        compose.onNodeWithContentDescription("What would you like?").performTextInput("Big ")
        compose.onNodeWithText("Save wish").performClick()
        assertEquals(listOf("name Big Wireless headphones", "save"), events.filterNot { it.endsWith(" ") })
    }

    @Test
    fun `errors stay with their fields, and a price error opens the details`() {
        show(adding.copy(showNameRequired = true, price = "x", priceError = PriceError.INVALID))

        compose.onNodeWithText("Enter what the wish is.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Enter a valid price", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `editing keeps delete in the header's options, not beside Save`() {
        show(editing)

        compose.onNodeWithText("Delete", substring = true).assertDoesNotExist()
        val more = compose.onNodeWithContentDescription("More options").getBoundsInRoot()
        assertTrue("the options sit in the header, above the title", more.bottom <= top("Edit wish"))

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete wish").performClick()
        assertEquals(listOf("delete"), events)
    }

    @Test
    fun `the confirmation names the wish, and cancel and delete reach the editor`() {
        show(editing.copy(name = "Headphones", confirmingDelete = true))

        compose.onNodeWithText("Delete Wireless headphones?").assertExists()
        compose.onNodeWithText("This wish will be removed from the list.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("dismiss", "confirm"), events)
    }

    @Test
    fun `a failed delete is said and the form stays`() {
        show(editing.copy(deleteFailed = true))

        compose.onNodeWithText("Could not delete. Please try again.").assertExists()
        compose.onNodeWithText("Save wish").assertExists()
        compose.mainClock.advanceTimeBy(10_000)
        assertTrue("the error is marked shown", "delete error shown" in events)
    }

    @Test
    fun `a wish that cannot be loaded keeps the way back`() {
        show(editing.copy(status = WishEditorStatus.LOAD_FAILED))

        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h3000dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme { WishEditorScreen(state = editing, actions = actions) }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Kívánság szerkesztése", "Ennyi elég – minden más nem kötelező.", "Hozzávetőleges ár", "Kívánság mentése").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }
}
