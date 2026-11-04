package app.giftiary.presentation.wishlists

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.presentation.sharing.OnlineContent
import app.giftiary.presentation.sharing.OnlineState
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.wishlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** The phone Lists root: the user's lists, then those shared with them, and their states. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h3000dp")
class WishlistsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val mine = wishlist("My wishlist")
    private val christmas = wishlist("Christmas 2026")
    private val martins = wishlist("Martin’s wishlist", personId = UUID.randomUUID())
    private val peters = SharedWishlistSummary(UUID.randomUUID(), "Peter’s wishlist", "Peter")
    private val unnamed = SharedWishlistSummary(UUID.randomUUID(), "Anna’s birthday", null)
    private val forAlex = SharedWishlistSummary(UUID.randomUUID(), "Birthday", "Bea", personName = "Alex")
    private val populated = WishlistsContent.Loaded(
        own = listOf(christmas, mine),
        forPeople = listOf(WishlistOfPerson(martins, "Martin")),
        wishCounts = mapOf(mine.id to 8, martins.id to 1),
    )
    private val sharedLoaded = OnlineState(OnlineContent.Loaded(listOf(peters, unnamed)))

    private val actions = mutableListOf<String>()
    private var content by mutableStateOf<WishlistsContent>(WishlistsContent.Loading)
    private var shared by mutableStateOf(OnlineState<List<SharedWishlistSummary>>())
    private var showAddButton by mutableStateOf(false)
    private var shown = false

    private fun show(
        content: WishlistsContent,
        shared: OnlineState<List<SharedWishlistSummary>> = sharedLoaded,
        showAddButton: Boolean = false,
    ) {
        this.content = content
        this.shared = shared
        this.showAddButton = showAddButton
        if (shown) return
        shown = true
        compose.setContent {
            GiftiaryTheme {
                WishlistsScreen(
                    content = this.content,
                    shared = this.shared,
                    actions = ListsActions(
                        onAddWishlist = { actions += "add" },
                        onOpenWishlist = { id -> actions += "open ${listOf(mine, christmas, martins).first { it.id == id }.name}" },
                        onOpenShared = { id -> actions += "shared ${listOf(peters, unnamed).first { it.id == id }.name}" },
                        onJoin = { actions += "join" },
                        onRetryShared = { actions += "retry" },
                        onSharedRefreshFailureShown = { actions += "refresh failure shown" },
                    ),
                    showAddButton = this.showAddButton,
                )
            }
        }
    }

    private fun top(text: String) = compose.onNodeWithText(text).getBoundsInRoot().top

    @Test
    fun `the root reads title, your lists, then the lists shared with you`() {
        show(populated)

        val order = listOf(
            "Lists", "Wish, plan and share with the people you love.", "Your lists",
            "Christmas 2026", "My wishlist", "Martin’s wishlist", "Shared with you", "Lists people have shared with you.",
            "Peter’s wishlist", "Anna’s birthday",
        )
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        listOf("Lists", "Your lists", "Shared with you").forEach {
            compose.onNodeWithText(it).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
    }

    @Test
    fun `counts come from the wishes, and no list claims whether it is shared`() {
        show(populated)

        compose.onNodeWithText("8 wishes").assertExists()
        compose.onNodeWithText("No wishes yet").assertExists()
        compose.onNodeWithText("For Martin · 1 wish").assertExists()
        // Any list may be shared, and whether it is is only known online, on its sharing screen.
        compose.onNodeWithText("Only you").assertDoesNotExist()
        compose.onNodeWithText("Shared with Peter").assertDoesNotExist()
    }

    @Test
    fun `covers are the branded monogram, hidden from screen readers`() {
        show(populated)

        // The initial is drawn, but the name next to it is what is read.
        compose.onNodeWithText("C").assertDoesNotExist()
        compose.onNodeWithText("Christmas 2026").assertExists()
    }

    @Test
    fun `shared lists name their owner, or a neutral member, never more`() {
        show(populated)

        compose.onNodeWithText("Peter").assertExists()
        compose.onNodeWithText("A Giftiary member").assertExists()
        compose.onNodeWithText("Peter · ", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a shared list kept for a person names whom it is for instead of its owner`() {
        show(populated, shared = OnlineState(OnlineContent.Loaded(listOf(forAlex, peters))))

        compose.onNodeWithText("For Alex").assertExists()
        compose.onNodeWithText("Bea").assertDoesNotExist()
        compose.onNodeWithText("Peter").assertExists()
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h3000dp")
    fun `in Hungarian a person's shared list reads as for them`() {
        show(populated, shared = OnlineState(OnlineContent.Loaded(listOf(forAlex))))

        compose.onNodeWithText("Alex részére").assertExists()
    }

    @Test
    fun `rows open their own list or the shared one`() {
        show(populated)

        compose.onNodeWithText("Martin’s wishlist").performClick()
        compose.onNodeWithText("Peter’s wishlist").performClick()

        assertEquals(listOf("open Martin’s wishlist", "shared Peter’s wishlist"), actions)
    }

    @Test
    fun `phones add through the global action, beside a rail the list keeps its add button`() {
        show(populated)
        compose.onNodeWithText("New wishlist").assertDoesNotExist()

        show(populated, showAddButton = true)
        compose.onNodeWithText("New wishlist").performClick()
        assertEquals(listOf("add"), actions)
    }

    @Test
    fun `no lists of one's own invites the first in place, and shared lists still show`() {
        show(WishlistsContent.Loaded(emptyList(), emptyList()))

        compose.onNodeWithText("No wishlists yet").assertExists()
        compose.onNodeWithText("Peter’s wishlist").assertExists()
        compose.onNodeWithText("New wishlist").performClick()
        assertEquals(listOf("add"), actions)
    }

    @Test
    fun `joining by link stays one tap away`() {
        show(populated)

        compose.onNodeWithContentDescription("Join a wishlist with an invite link").performClick()
        assertEquals(listOf("join"), actions)
    }

    @Test
    fun `nothing shared says how sharing starts`() {
        show(populated, shared = OnlineState(OnlineContent.Loaded(emptyList())))

        compose.onNodeWithText("When someone invites you", substring = true).assertExists()
        compose.onNodeWithText("My wishlist").assertExists()
    }

    @Test
    fun `shared lists that cannot be read offer a retry, and the own lists stay`() {
        show(populated, shared = OnlineState(OnlineContent.Failed(SharingFailure.NETWORK)))

        compose.onNodeWithText("My wishlist").assertExists()
        compose.onNodeWithText("Peter’s wishlist").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), actions)
    }

    @Test
    fun `a failed refresh keeps the shared lists shown`() {
        show(populated, shared = sharedLoaded.copy(refreshFailure = SharingFailure.NETWORK))

        compose.onNodeWithText("Peter’s wishlist").assertExists()
        compose.onNodeWithText("Couldn’t reach Giftiary. Check your connection and try again.").assertExists()
    }

    @Test
    fun `long Hungarian names at large text stay inside the screen`() {
        val long = wishlist("Karácsonyi kívánságlista a nagyszülőknek és unokatestvéreknek")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme {
                    WishlistsScreen(
                        content = WishlistsContent.Loaded(listOf(long), emptyList()),
                        shared = sharedLoaded,
                        actions = ListsActions({}, {}, {}, {}, {}, {}),
                        showAddButton = false,
                    )
                }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf(long.name, "Shared with you").forEach {
            val bounds = compose.onNodeWithText(it).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
        assertTrue(compose.onNodeWithContentDescription("Join a wishlist with an invite link").getBoundsInRoot().right <= width)
    }

    @Test
    fun `only unreadable own lists are a full message`() {
        show(WishlistsContent.LoadFailed)

        compose.onNodeWithText("Your wishlists could not be loaded.").assertExists()
        compose.onNodeWithText("Your lists").assertDoesNotExist()
    }
}
