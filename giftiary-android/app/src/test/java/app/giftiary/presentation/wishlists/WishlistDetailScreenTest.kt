package app.giftiary.presentation.wishlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Wish
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.WithFontScale
import app.giftiary.testing.lineCount
import app.giftiary.testing.wish
import app.giftiary.testing.wishlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import org.robolectric.annotation.GraphicsMode

/** The owner's wishlist: identity, sharing, the wishes, and where every owner action went. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h3000dp")
class WishlistDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val peterId = UUID.randomUUID()
    private val mine = wishlist("My wishlist")
    private val peters = wishlist("Peter’s wishlist", personId = peterId)
    private val headphones = wish(mine.id, "Wireless headphones", "Something comfortable", Money(45000, "HUF"), "https://example.com/h")
    private val coffee = wish(mine.id, "Coffee subscription", "Medium roast")

    private val actions = mutableListOf<String>()
    private var state by mutableStateOf(WishlistDetailUiState())
    private var shown = false
    private var fontScale = 1f

    private fun show(state: WishlistDetailUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent {
            WithFontScale(fontScale) {
                GiftiaryTheme {
                    WishlistDetailScreen(
                        state = this.state,
                        actions = WishlistDetailScreenActions(
                            onBack = { actions += "back" },
                            onEditList = { actions += "edit list" },
                            onOpenPerson = { actions += "person" },
                            onAddWish = { actions += "add wish" },
                            onOpenWish = { id -> actions += "open ${name(id)}" },
                            onEditWish = { id -> actions += "edit ${name(id)}" },
                            onShare = { actions += "share" },
                            onDeleteRequested = { actions += "delete requested" },
                            onDeleteConfirmed = { actions += "delete confirmed" },
                            onDeleteDismissed = { actions += "delete dismissed" },
                            onWriteErrorShown = { actions += "error shown" },
                        ),
                    )
                }
            }
        }
    }

    private fun name(id: UUID) = listOf(headphones, coffee).first { it.id == id }.name

    private fun own(vararg wishes: Wish, waitingToSync: Boolean = false) =
        WishlistDetailUiState(WishlistDetailContent.Loaded(mine, WishlistOwner.Me, null, wishes.toList()), waitingToSync = waitingToSync)

    private fun forPeter() =
        WishlistDetailUiState(WishlistDetailContent.Loaded(peters, WishlistOwner.Person("Peter"), peterId, listOf(headphones)))

    private fun top(text: String) = compose.onNodeWithText(text).getBoundsInRoot().top

    @Test
    fun `the list reads identity, sharing, then its wishes in order`() {
        show(own(headphones, coffee))

        val order = listOf("My wishlist", "Your wishlist · 2 wishes", "Sharing", "Wishes", "Wireless headphones", "Coffee subscription")
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        compose.onNodeWithText("My wishlist").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Wishes").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h3000dp")
    // Real text measurement, so a squeezed or clipped word shows.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in Hungarian at large text the heading, sharing line and a wish's link keep their words whole`() {
        fontScale = 2f
        show(own(headphones))

        val width = compose.onRoot().getBoundsInRoot().right
        // Each moves under its text rather than squeezing it.
        listOf("Kívánságok", "Megosztás", "Kívánság hozzáadása").forEach {
            val text = compose.onNodeWithText(it, useUnmergedTree = true)
            assertEquals(it, 1, text.lineCount())
            assertTrue("$it inside the screen", text.getBoundsInRoot().right <= width)
        }
        // The link wraps under the price, whole, and does not reach into the price's line.
        val link = compose.onNodeWithText("Link megnyitása", useUnmergedTree = true)
        assertTrue("link on at most two lines", link.lineCount() <= 2)
        val price = compose.onNodeWithText("kb.", substring = true, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("link under the price", link.getBoundsInRoot().top >= price.bottom)
    }

    @Test
    fun `a wish shows its note, estimated price and link`() {
        show(own(headphones))

        compose.onNodeWithText("Something comfortable").assertExists()
        compose.onNodeWithText("~", substring = true).assertExists()
        compose.onNodeWithText("45,000", substring = true).assertExists()
        compose.onNodeWithText("View link").assertExists()
    }

    @Test
    fun `adding a wish is beside the wishes heading`() {
        show(own(headphones))

        compose.onNodeWithContentDescription("Add a wish to this wishlist").performClick()
        assertEquals(listOf("add wish"), actions)
    }

    @Test
    fun `a wish opens its detail, and its options edit it`() {
        show(own(headphones, coffee))

        compose.onNodeWithText("Coffee subscription").performClick()
        compose.onNodeWithContentDescription("Options for Wireless headphones").performClick()
        compose.onNodeWithText("Edit wish").performClick()

        assertEquals(listOf("open Coffee subscription", "edit Wireless headphones"), actions)
    }

    @Test
    fun `sharing is the header's one action, while editing and deleting wait in the list's options`() {
        show(own(headphones))

        compose.onNodeWithText("Sharing").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Options for My wishlist").performClick()
        compose.onNodeWithText("Edit list").performClick()
        compose.onNodeWithContentDescription("Options for My wishlist").performClick()
        compose.onNodeWithText("Share").assertDoesNotExist()
        compose.onNodeWithText("Delete wishlist").performClick()

        assertEquals(listOf("share", "edit list", "delete requested"), actions)
    }

    @Test
    fun `a list kept for a person is shared like any other, and opens that person`() {
        show(forPeter())

        compose.onNodeWithText("Sharing").performClick()
        compose.onNodeWithText("Peter · 1 wish").performClick()

        assertEquals(listOf("share", "person"), actions)
    }

    @Test
    fun `deleting still asks first`() {
        show(own(headphones).copy(actions = WishlistDetailActions(confirmingDelete = true)))

        compose.onNodeWithText("Delete My wishlist?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("delete confirmed"), actions)
    }

    @Test
    fun `an empty list invites its first wish`() {
        show(own())

        compose.onNodeWithText("No wishes yet. Add the first thing on this list.").assertExists()
        compose.onNodeWithText("Your wishlist · No wishes yet").assertExists()
        compose.onNodeWithText("Add wish").assertExists()
    }

    @Test
    fun `a change not yet synced says it is saved on this device, never failed`() {
        show(own(headphones))
        compose.onNodeWithText("Waiting to sync", substring = true).assertDoesNotExist()

        show(own(headphones, waitingToSync = true))
        compose.onNodeWithText("Waiting to sync · Saved on this device").assertExists()
        compose.onNodeWithText("fail", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `back is the aqua circle`() {
        show(own(headphones))

        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), actions)
    }

    @Test
    fun `a list that is gone or unreadable says so`() {
        show(WishlistDetailUiState(WishlistDetailContent.NotFound))
        compose.onNodeWithText("This wishlist no longer exists.").assertExists()

        show(WishlistDetailUiState(WishlistDetailContent.LoadFailed))
        compose.onNodeWithText("The wishlist could not be loaded.").assertExists()
    }
}
