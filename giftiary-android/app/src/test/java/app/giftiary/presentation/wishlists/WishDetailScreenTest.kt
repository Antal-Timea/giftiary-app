package app.giftiary.presentation.wishlists

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Wish
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/** The owner's wish: its list, the facts it has and the note; edit and delete in the options. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2000dp")
class WishDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val full = Wish(
        id = UUID.randomUUID(),
        wishlistId = UUID.randomUUID(),
        name = "Wireless headphones",
        note = "Over-ear, black.",
        estimatedPrice = Money(45000, "HUF"),
        productUrl = "https://example.org/headphones",
        photoPath = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
    private val sparse = full.copy(note = null, estimatedPrice = null, productUrl = null)

    private val events = mutableListOf<String>()
    private var state by mutableStateOf(WishDetailUiState())
    private var shown = false

    private fun show(state: WishDetailUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent {
            GiftiaryTheme {
                WishDetailScreen(
                    state = this.state,
                    onBack = { events += "back" },
                    onEdit = { events += "edit" },
                    onDeleteRequested = { events += "delete" },
                    onDeleteConfirmed = { events += "confirm" },
                    onDeleteDismissed = { events += "dismiss" },
                    onWriteErrorShown = { events += "error shown" },
                )
            }
        }
    }

    private fun loaded(wish: Wish, waitingToSync: Boolean = false) =
        WishDetailUiState(content = WishDetailContent.Loaded(wish, "Peter’s wishlist"), waitingToSync = waitingToSync)

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    @Test
    fun `a full wish reads its list, price, link, then the note`() {
        show(loaded(full))

        val order = listOf(
            "WISH", "Wireless headphones", "On Peter’s wishlist", "Approximate price", "Product link", "View link", "Over-ear, black.",
        )
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        compose.onNodeWithText("~", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("https://example.org/headphones", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a sparse wish shows only what it has, with no photo and no primary action`() {
        show(loaded(sparse))

        compose.onNodeWithText("On Peter’s wishlist").assertExists()
        listOf("Approximate price", "Product link", "View link").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        assertEquals(0, compose.onAllNodes(hasContentDescription("photo", substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
        compose.onNodeWithText("Edit wish").assertDoesNotExist()
    }

    @Test
    fun `the owner sees nothing of reservations`() {
        show(loaded(full))

        listOf("reserv", "getting this", "I’ll get this").forEach {
            assertEquals(it, 0, compose.onAllNodes(hasText(it, substring = true, ignoreCase = true)).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `waiting to sync is said only while the wish's change waits`() {
        show(loaded(full))
        compose.onNodeWithText("Waiting to sync", substring = true, useUnmergedTree = true).assertDoesNotExist()

        show(loaded(full, waitingToSync = true))
        compose.onNodeWithText("Waiting to sync", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `edit and delete wait in the options, and delete still asks`() {
        show(loaded(full))

        compose.onNodeWithContentDescription("Options for Wireless headphones").performClick()
        compose.onNodeWithText("Edit wish").performClick()
        compose.onNodeWithContentDescription("Options for Wireless headphones").performClick()
        compose.onNodeWithText("Delete wish").performClick()
        show(loaded(full).copy(actions = WishDetailActions(confirmingDelete = true)))
        compose.onNodeWithText("Delete Wireless headphones?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf("edit", "delete", "dismiss", "confirm"), events)
    }

    @Test
    fun `a failed delete is said and the wish stays`() {
        show(loaded(full).copy(actions = WishDetailActions(writeFailed = true)))

        compose.onNodeWithText("Could not delete. Please try again.").assertExists()
        compose.onNodeWithText("Wireless headphones").assertExists()
        compose.mainClock.advanceTimeBy(10_000)
        assertTrue("error shown" in events)
    }

    @Test
    fun `a wish that is gone keeps the way back`() {
        show(WishDetailUiState(content = WishDetailContent.NotFound))

        compose.onNodeWithText("This wish no longer exists.").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h2000dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme { WishDetailScreen(loaded(full), {}, {}, {}, {}, {}, {}) }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("KÍVÁNSÁG", "Ezen a listán: Peter’s wishlist", "Hozzávetőleges ár").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }
}
