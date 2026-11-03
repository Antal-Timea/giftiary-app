package app.giftiary.presentation.sharing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.InviteToken
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.FakeWishlistSharingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

/** Joining a shared list: the one Join, Try again only when it may help, and nothing about the list. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class JoinWishlistScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val link = "giftiary://wishlist/join#" + "A".repeat(43)
    private val events = mutableListOf<String>()
    private var state by mutableStateOf(JoinWishlistUiState())
    private var shown = false

    private fun show(state: JoinWishlistUiState, fontScale: Float = 1f) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GiftiaryTheme {
                    JoinWishlistScreen(
                        state = this.state,
                        onBack = { events += "back" },
                        onInputChange = { events += "input" },
                        onJoin = { events += "join" },
                        onRetry = { events += "retry" },
                    )
                }
            }
        }
    }

    private fun joinButton() = compose.onNode(hasText("Join wishlist") and hasClickAction())

    @Test
    fun `the task header names the flow and goes back`() {
        show(JoinWishlistUiState())

        compose.onNode(hasText("Join a wishlist") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        compose.onNodeWithText("Paste the invite link you received.").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    fun `Join waits for a link, then asks once`() {
        show(JoinWishlistUiState())
        joinButton().assertIsNotEnabled()

        show(JoinWishlistUiState(input = link))
        joinButton().assertIsEnabled().performClick()

        assertEquals(listOf("join"), events)
    }

    @Test
    fun `while joining, neither the link nor Join can be used again`() {
        show(JoinWishlistUiState(input = link, phase = JoinPhase.Joining))

        joinButton().assertIsNotEnabled().performClick()
        compose.onNodeWithText(link).assertIsNotEnabled()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `an opened link joins on the button, which cannot be pressed meanwhile`() {
        show(JoinWishlistUiState(phase = JoinPhase.Joining))

        joinButton().assertIsNotEnabled().performClick()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a failure that may pass offers Try again`() {
        show(JoinWishlistUiState(phase = JoinPhase.Failed(JoinFailure.NETWORK)))

        compose.onNodeWithText("Couldn’t reach Giftiary. Check your connection and try again.").assertExists()
        compose.onNode(hasText("Try again") and hasClickAction()).performClick()
        assertEquals(listOf("retry"), events)

        show(JoinWishlistUiState(phase = JoinPhase.Failed(JoinFailure.SERVER)))
        compose.onNode(hasText("Try again") and hasClickAction()).assertExists()
    }

    @Test
    fun `an unavailable invite is final and cannot be joined from the opened link`() {
        show(JoinWishlistUiState(phase = JoinPhase.Failed(JoinFailure.UNAVAILABLE)))

        compose.onNodeWithText("This invite isn’t available", substring = true).assertExists()
        compose.onAllNodesWithText("Try again").assertCountEquals(0)
        joinButton().assertIsNotEnabled().performClick()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a link that is not an invite says so, without Try again`() {
        show(JoinWishlistUiState(phase = JoinPhase.Failed(JoinFailure.INVALID_LINK)))

        compose.onNodeWithText("This isn’t a valid invite link.").assertExists()
        compose.onAllNodesWithText("Try again").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h1600dp")
    fun `in Hungarian at large text the page stays inside the screen`() {
        show(JoinWishlistUiState(input = link, phase = JoinPhase.Failed(JoinFailure.NETWORK)), fontScale = 2f)

        val width = compose.onRoot().getBoundsInRoot().right
        listOf(
            compose.onNodeWithText("Csatlakozás kívánságlistához"),
            compose.onNodeWithText("Illeszd be a kapott meghívó linket."),
            compose.onNodeWithText("Nem sikerült elérni a Giftiaryt", substring = true),
            compose.onNode(hasText("Csatlakozás") and hasClickAction()),
            compose.onNode(hasText("Újra") and hasClickAction()),
        ).forEach { node ->
            val bounds = node.getBoundsInRoot()
            assertTrue("$bounds outside the screen", bounds.left >= 0.dp && bounds.right <= width)
        }
        val join = compose.onNode(hasText("Csatlakozás") and hasClickAction()).getBoundsInRoot()
        assertTrue(join.bottom - join.top >= 48.dp)
    }

    @Test
    fun `an opened link that joins leads to the joined list, once`() {
        val sharing = FakeWishlistSharingRepository()
        val pending = PendingWishlistJoin().apply { offer(PendingJoin.Invite(InviteToken.of("A".repeat(43))!!)) }
        val viewModel = JoinWishlistViewModel(sharing, pending)
        val joined = mutableListOf<UUID>()

        compose.setContent {
            GiftiaryTheme {
                JoinWishlistScreen(viewModel = viewModel, onBack = {}, onJoined = { joined += it })
            }
        }
        compose.waitForIdle()

        assertEquals(listOf(sharing.redeemedWishlistId), joined)
    }
}
