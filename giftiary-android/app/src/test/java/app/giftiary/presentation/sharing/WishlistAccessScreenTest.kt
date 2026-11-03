package app.giftiary.presentation.sharing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.giftiary.domain.model.ActiveInvite
import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.model.WishlistMember
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

/** Share & Invite: the people with access, sharing through an invite link, and the open links. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2000dp")
class WishlistAccessScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-17T10:00:00Z")
    private val anna = WishlistMember(UUID.randomUUID(), "Anna", now)
    private val unnamed = WishlistMember(UUID.randomUUID(), null, now)
    private val invite = ActiveInvite(UUID.randomUUID(), now, now.plusSeconds(7 * 86_400))
    private val token = InviteToken.of("A".repeat(43))!!
    private val loaded = WishlistAccessUiState(content = AccessContent.Loaded(WishlistAccess(listOf(invite), listOf(anna, unnamed))))

    private val events = mutableListOf<String>()
    private var state by mutableStateOf(loaded)
    private var shown = false

    private val actions = WishlistAccessActions(
        onBack = { events += "back" },
        onRetry = { events += "retry" },
        onCreateInvite = { events += "create" },
        onShare = { events += "share ${it.id}" },
        onRevokeInvite = { events += "revoke $it" },
        onRemoveRequested = { events += "remove ${it.displayName}" },
        onRemoveConfirmed = { events += "remove confirmed" },
        onRemoveDismissed = { events += "remove dismissed" },
        onMessageShown = {},
        onAddName = { events += "add name" },
        onNamePromptDismissed = { events += "not now" },
    )

    private fun show(state: WishlistAccessUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent { GiftiaryTheme { WishlistAccessScreen(state = this.state, wishlistName = "Peter’s wishlist", actions = actions) } }
    }

    private fun date(instant: Instant) =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US).format(instant.atZone(ZoneId.systemDefault()))

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    @Test
    fun `reads title, list, people with access, share with someone, open links, then the reservations note`() {
        show(loaded)

        val order = listOf(
            "Share wishlist", "Peter’s wishlist", "People with access", "Anna", "A Giftiary member", "Share with someone",
            "Open invites", "Invite link",
            "They can plan gifts together. You won’t see what they reserve, so the surprise stays.",
        )
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        compose.onNodeWithText("GIFTIARY").assertDoesNotExist()
        assertEquals(2, compose.onAllNodesWithText("Joined ${date(now)}").fetchSemanticsNodes().size)
    }

    @Test
    fun `share with someone creates an invite link`() {
        show(loaded)

        compose.onNodeWithText("Share with someone").performClick()
        assertEquals(listOf("create"), events)
    }

    @Test
    fun `removing a person is named and sits behind their options`() {
        show(loaded)

        compose.onNodeWithText("Remove Anna").assertDoesNotExist()
        compose.onNodeWithContentDescription("Options for Anna").performClick()
        compose.onNodeWithText("Remove Anna").performClick()
        assertEquals(listOf("remove Anna"), events)
    }

    @Test
    fun `the removal confirmation reaches the screen`() {
        show(loaded.copy(confirmingRemoval = anna))

        compose.onNodeWithText("Remove Anna?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Remove").performClick()
        assertEquals(listOf("remove dismissed", "remove confirmed"), events)
    }

    @Test
    fun `withdrawing an open link sits behind its options`() {
        show(loaded)

        val created = date(invite.createdAt)
        compose.onNodeWithContentDescription("Options for the invite created $created").performClick()
        compose.onNodeWithText("Withdraw the invite created $created").performClick()
        assertEquals(listOf("revoke ${invite.id}"), events)
    }

    @Test
    fun `a new link is shared through the system, and its token is never shown`() {
        show(loaded.copy(newInvite = CreatedInvite(invite.id, token, invite.expiresAt)))

        compose.onNodeWithText("Invite link ready").assertExists()
        compose.onNodeWithText("Share with someone").assertDoesNotExist()
        compose.onNodeWithText("A".repeat(43), substring = true).assertDoesNotExist()
        compose.onNodeWithText("Share link").performClick()
        assertEquals(listOf("share ${invite.id}"), events)
    }

    @Test
    fun `with nobody yet and no links, it says so and offers sharing`() {
        show(WishlistAccessUiState(content = AccessContent.Loaded(WishlistAccess(emptyList(), emptyList()))))

        compose.onNodeWithText("No one has joined yet.").assertExists()
        compose.onNodeWithText("Open invites").assertDoesNotExist()
        compose.onNodeWithText("Share with someone").assertExists()
    }

    @Test
    fun `a failed load stays a failure, with retry and the way back`() {
        show(WishlistAccessUiState(content = AccessContent.Failed(SharingFailure.NETWORK)))

        compose.onNodeWithText("Couldn’t reach Giftiary. Check your connection and try again.").assertExists()
        compose.onNodeWithText("Share with someone").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("retry", "back"), events)
    }

    @Test
    fun `the name prompt asks without blocking sharing`() {
        show(loaded.copy(ownerNameMissing = true))

        compose.onNodeWithText("Share with someone").assertExists()
        compose.onNodeWithText("Add name").performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(listOf("add name", "not now"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h2000dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme { WishlistAccessScreen(state = loaded, wishlistName = "Péter születésnapi kívánságlistája", actions = actions) }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Lista megosztása", "Megosztás valakivel", "Péter születésnapi kívánságlistája").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }
}
