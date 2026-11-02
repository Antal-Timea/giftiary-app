package app.giftiary.presentation.sharing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.ReservationState
import app.giftiary.domain.model.SharedWish
import app.giftiary.domain.model.SharedWishlist
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** A list shared with the user: the owner's context, no owner controls, and the guest's reservations. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h3000dp")
class SharedWishlistScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val headphones = SharedWish(UUID.randomUUID(), "Wireless headphones", "For travelling", Money(45000, "HUF"), "https://example.com/h", ReservationState.AVAILABLE)
    private val coffee = SharedWish(UUID.randomUUID(), "Coffee subscription", null, null, null, ReservationState.RESERVED_BY_ME)
    private val backpack = SharedWish(UUID.randomUUID(), "Running backpack", null, null, null, ReservationState.RESERVED_BY_OTHER)
    private val unknown = SharedWish(UUID.randomUUID(), "Mystery", null, null, null, null)

    private val actions = mutableListOf<String>()
    private var state by mutableStateOf(OnlineState<SharedWishlist>())
    private var reservations by mutableStateOf(ReservationActions())
    private var leaving by mutableStateOf(LeaveState())
    private var shown = false

    private fun show(
        state: OnlineState<SharedWishlist>,
        reservations: ReservationActions = ReservationActions(),
        leaving: LeaveState = LeaveState(),
    ) {
        this.state = state
        this.reservations = reservations
        this.leaving = leaving
        if (shown) return
        shown = true
        compose.setContent {
            GiftiaryTheme {
                SharedWishlistScreen(
                    state = this.state,
                    leaving = this.leaving,
                    reservations = this.reservations,
                    actions = SharedWishlistActions(
                        onBack = { actions += "back" },
                        onRetry = { actions += "retry" },
                        onRefreshFailureShown = {},
                        onLeaveRequested = { actions += "leave requested" },
                        onLeaveConfirmed = { actions += "leave confirmed" },
                        onLeaveDismissed = {},
                        onLeaveFailureShown = {},
                        onReserve = { id -> actions += "reserve ${name(id)}" },
                        onReleaseRequested = { id -> actions += "release ${name(id)}" },
                        onReleaseConfirmed = { actions += "release confirmed" },
                        onReleaseDismissed = {},
                        onReservationMessageShown = {},
                    ),
                )
            }
        }
    }

    private fun name(id: UUID) = listOf(headphones, coffee, backpack, unknown).first { it.id == id }.name

    private fun loaded(vararg wishes: SharedWish, owner: String? = "Peter") =
        OnlineState(OnlineContent.Loaded(SharedWishlist(UUID.randomUUID(), "Peter’s wishlist", owner, wishes.toList())))

    private fun top(text: String) = compose.onNodeWithText(text).getBoundsInRoot().top

    @Test
    fun `the list reads its owner's context, then the wishes in the owner's order`() {
        show(loaded(headphones, coffee, backpack))

        val order = listOf("Peter’s wishlist", "Peter · 3 wishes", "Wishes", "Wireless headphones", "Coffee subscription", "Running backpack")
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        compose.onNodeWithText("Peter’s wishlist").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `an owner without a name is a neutral member`() {
        show(loaded(headphones, owner = null))

        compose.onNodeWithText("A Giftiary member · 1 wish").assertExists()
    }

    @Test
    fun `a guest has none of the owner's controls`() {
        show(loaded(headphones, coffee))

        listOf("Add wish", "Manage", "Edit list", "Edit wish", "Delete wishlist", "Share").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("Add a wish to this wishlist").assertDoesNotExist()
        compose.onNodeWithContentDescription("Options for Wireless headphones").assertDoesNotExist()
        // Wishes have no detail screen for guests: the row itself does not open anything.
        compose.onNodeWithText("Wireless headphones").assert(!hasClickAction())
    }

    @Test
    fun `each reservation is said in words and offers only its own action`() {
        show(loaded(headphones, coffee, backpack, unknown))

        compose.onNodeWithContentDescription("I’ll get Wireless headphones")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Available"))
        compose.onNodeWithText("You’re getting this").assertExists()
        compose.onNodeWithText("Reserved").assertExists()

        compose.onNodeWithContentDescription("I’ll get Wireless headphones").performClick()
        compose.onNodeWithContentDescription("Undo: you’re no longer getting Coffee subscription").performClick()
        assertEquals(listOf("reserve Wireless headphones", "release Coffee subscription"), actions)
    }

    @Test
    fun `someone else's reservation says nothing about who and offers nothing`() {
        show(loaded(backpack))

        compose.onNodeWithText("Reserved").assert(!hasClickAction())
        compose.onNodeWithText("I’ll get this").assertDoesNotExist()
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithText("by", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a state this version does not know shows and offers nothing`() {
        show(loaded(unknown))

        compose.onNodeWithText("Mystery").assertExists()
        compose.onNode(hasText("I’ll get this") or hasText("Reserved") or hasText("You’re getting this")).assertDoesNotExist()
    }

    @Test
    fun `while the server decides, the last state stays and its action waits`() {
        show(loaded(headphones, coffee), reservations = ReservationActions(inFlight = setOf(headphones.id, coffee.id)))

        compose.onNodeWithContentDescription("I’ll get Wireless headphones").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Undo: you’re no longer getting Coffee subscription").assertIsNotEnabled()
        compose.onNodeWithText("You’re getting this").assertExists()
    }

    @Test
    fun `releasing still asks first`() {
        show(loaded(coffee), reservations = ReservationActions(confirmingRelease = coffee.id))

        compose.onNodeWithText("Release Coffee subscription?").assertExists()
        compose.onNodeWithText("Release").performClick()
        assertEquals(listOf("release confirmed"), actions)
    }

    @Test
    fun `leaving waits in the list's options and asks first`() {
        show(loaded(headphones))

        compose.onNodeWithContentDescription("Options for Peter’s wishlist").performClick()
        compose.onNodeWithText("Leave this wishlist").performClick()
        assertEquals(listOf("leave requested"), actions)

        show(loaded(headphones), leaving = LeaveState(confirming = true))
        compose.onNodeWithText("Leave Peter’s wishlist?").assertExists()
    }

    @Test
    fun `a list without wishes says so`() {
        show(loaded())

        compose.onNodeWithText("No wishes on this list yet.").assertExists()
        compose.onNodeWithText("Peter · No wishes yet").assertExists()
    }

    @Test
    fun `a list that cannot be read keeps back, and retries only when that may help`() {
        show(OnlineState(OnlineContent.Failed(SharingFailure.NETWORK)))
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        show(OnlineState(OnlineContent.Failed(SharingFailure.UNAVAILABLE)))
        compose.onNodeWithText("This wishlist isn’t available.").assertExists()
        compose.onNodeWithText("Try again").assertDoesNotExist()

        assertEquals(listOf("retry", "back"), actions)
    }

    @Test
    fun `back leaves the list`() {
        show(loaded(headphones))

        compose.onNode(hasClickAction() and hasContentDescriptionExactly("Back")).performClick()
        assertEquals(listOf("back"), actions)
    }

    private fun hasContentDescriptionExactly(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))
}
