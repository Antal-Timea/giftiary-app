package app.giftiary.presentation.sharing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import app.giftiary.domain.model.ActiveInvite
import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.EmailInvite
import app.giftiary.domain.model.EmailInviteState
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.model.WishlistMember
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val bob = EmailInvite(UUID.randomUUID(), "bob@example.com", EmailInviteState.PENDING, now, now.plusSeconds(14 * 86_400))
    private val eve = EmailInvite(UUID.randomUUID(), "eve@example.com", EmailInviteState.NOT_ACCEPTED, now, now.plusSeconds(14 * 86_400))
    private val loaded = WishlistAccessUiState(
        content = AccessContent.Loaded(WishlistAccess(listOf(invite), listOf(anna, unnamed))),
        emailInvites = EmailInvitesContent.Loaded(listOf(bob, eve)),
    )

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
        onEmailInviteOpened = { events += "email open" },
        onEmailChanged = { events += "email $it" },
        onEmailInviteDismissed = { events += "email cancel" },
        onSendEmailInvite = { events += "email send" },
        onRevokeEmailInvite = { events += "email revoke $it" },
        onInvitedNoticeShown = { events += "notice shown" },
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
    fun `reads title, list, people with access, both ways to invite, email invites, links, then the reservations note`() {
        show(loaded)

        val order = listOf(
            "Share wishlist", "Peter’s wishlist", "People with access", "Anna", "A Giftiary member", "Invite by email address", "Share invite link",
            "Email invites", "bob@example.com", "eve@example.com", "Invite links", "Invite link",
            "They can plan gifts together. You won’t see what they reserve, so the surprise stays.",
        )
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        compose.onNodeWithText("GIFTIARY").assertDoesNotExist()
        assertEquals(2, compose.onAllNodesWithText("Joined ${date(now)}").fetchSemanticsNodes().size)
    }

    @Test
    fun `sharing an invite link still creates one`() {
        show(loaded)

        compose.onNodeWithText("Share invite link").performClick()
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
        compose.onNodeWithText("Share invite link").assertDoesNotExist()
        compose.onNodeWithText("A".repeat(43), substring = true).assertDoesNotExist()
        compose.onNodeWithText("Share link").performClick()
        assertEquals(listOf("share ${invite.id}"), events)
    }

    @Test
    fun `with nobody yet and no links, it says so and offers sharing`() {
        show(
            WishlistAccessUiState(
                content = AccessContent.Loaded(WishlistAccess(emptyList(), emptyList())),
                emailInvites = EmailInvitesContent.Loaded(emptyList()),
            ),
        )

        compose.onNodeWithText("No one has joined yet.").assertExists()
        compose.onNodeWithText("Invite links").assertDoesNotExist()
        compose.onNodeWithText("Email invites").assertDoesNotExist()
        compose.onNodeWithText("Share invite link").assertExists()
        compose.onNodeWithText("Invite by email address").assertExists()
    }

    @Test
    fun `both ways to invite are equal buttons of their own`() {
        show(loaded)

        compose.onNodeWithText("Invite by email address").assertHasClickAction().performClick()
        compose.onNodeWithText("Share invite link").assertHasClickAction().performClick()
        assertEquals(listOf("email open", "create"), events)
        val email = compose.onNodeWithText("Invite by email address").getBoundsInRoot()
        val link = compose.onNodeWithText("Share invite link").getBoundsInRoot()
        assertEquals("same start", email.left, link.left)
    }

    @Test
    fun `the email form takes an address and sends or cancels`() {
        show(loaded.copy(emailForm = EmailInviteForm(text = "bob@")))

        compose.onNodeWithText("No email is sent. The invite appears in Giftiary for whoever signs in with this address.").assertExists()
        compose.onNodeWithText("bob@").performTextReplacement("bob@example.com")
        compose.onNodeWithText("Save invite").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("email bob@example.com", "email send", "email cancel"), events)
        compose.onNodeWithText("Share invite link").assertExists()
    }

    @Test
    fun `email form errors are written out and never mention an account`() {
        val texts = mapOf(
            EmailInviteError.INVALID to "Enter a valid email address.",
            EmailInviteError.NOT_ALLOWED to "This address can’t be invited. You can’t invite yourself.",
            EmailInviteError.LIMIT to "You can’t send more email invites right now. Withdraw one or try again tomorrow.",
            EmailInviteError.NETWORK to "Couldn’t reach Giftiary. Check your connection and try again.",
        )
        texts.forEach { (error, text) ->
            show(loaded.copy(emailForm = EmailInviteForm(text = "bob@example.com", error = error)))
            compose.onNodeWithText(text).assertExists()
            assertFalse(text, text.contains("account", ignoreCase = true))
        }
    }

    @Test
    fun `an email invite reads pending or not accepted, nothing more`() {
        show(loaded)

        compose.onNodeWithText("Pending · until ${date(bob.expiresAt)}").assertExists()
        compose.onNodeWithText("Not accepted").assertExists()
        listOf("Declined", "Expired", "Accepted").forEach { compose.onNodeWithText(it, substring = true).assertDoesNotExist() }
    }

    @Test
    fun `a pending email invite is withdrawn and a not accepted one cleared, behind their options`() {
        show(loaded)

        compose.onNodeWithContentDescription("Options for the invite to bob@example.com").performClick()
        compose.onNodeWithText("Withdraw the invite to bob@example.com").performClick()
        compose.onNodeWithContentDescription("Options for the invite to eve@example.com").performClick()
        compose.onNodeWithText("Remove eve@example.com from this list").performClick()
        assertEquals(listOf("email revoke ${bob.id}", "email revoke ${eve.id}"), events)
    }

    @Test
    fun `email invites that could not be loaded leave everything else and offer a retry`() {
        show(loaded.copy(emailInvites = EmailInvitesContent.Failed(SharingFailure.SERVER)))

        compose.onNodeWithText("Email invites couldn’t be loaded.").assertExists()
        compose.onNodeWithText("Share invite link").assertExists()
        compose.onNodeWithText("Invite link").assertExists()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), events)
    }

    @Test
    @Config(qualifiers = "w320dp-h2000dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a long address stays on one line, cut short, at large text, still named in full`() {
        val long = EmailInvite(UUID.randomUUID(), "bartholomew.longname@gmail.com", EmailInviteState.PENDING, now, now.plusSeconds(86_400))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme {
                    WishlistAccessScreen(
                        state = loaded.copy(emailInvites = EmailInvitesContent.Loaded(listOf(long))),
                        wishlistName = "Peter’s wishlist",
                        actions = actions,
                    )
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(long.email, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(1, layout.lineCount)
        assertTrue("cut short at this width", layout.isLineEllipsized(0))
        assertTrue(compose.onNodeWithText(long.email, useUnmergedTree = true).getBoundsInRoot().right <= compose.onRoot().getBoundsInRoot().right)
        compose.onNodeWithContentDescription("Options for the invite to ${long.email}").assertExists()
    }

    @Test
    fun `a sent invite is confirmed the same way for any address`() {
        show(loaded.copy(invitedEmail = "nobody@example.com"))

        compose.onNodeWithText("Invite saved for nobody@example.com.").assertExists()
    }

    @Test
    fun `a failed load stays a failure, with retry and the way back`() {
        show(WishlistAccessUiState(content = AccessContent.Failed(SharingFailure.NETWORK)))

        compose.onNodeWithText("Couldn’t reach Giftiary. Check your connection and try again.").assertExists()
        compose.onNodeWithText("Share invite link").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("retry", "back"), events)
    }

    @Test
    fun `the name prompt asks without blocking sharing`() {
        show(loaded.copy(ownerNameMissing = true))

        compose.onNodeWithText("Share invite link").assertExists()
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
                GiftiaryTheme {
                    WishlistAccessScreen(
                        state = loaded.copy(emailForm = EmailInviteForm(error = EmailInviteError.LIMIT)),
                        wishlistName = "Péter születésnapi kívánságlistája",
                        actions = actions,
                    )
                }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf(
            "Lista megosztása", "Meghívó link megosztása", "Péter születésnapi kívánságlistája", "Meghívás email-cím alapján",
            "Most nem küldhetsz több email-meghívót. Vonj vissza egyet, vagy próbáld újra holnap.", "Meghívó mentése",
            "Emailt nem küldünk. A meghívó a Giftiaryben jelenik meg annak, aki ezzel a címmel jelentkezik be.",
        ).forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }
}
