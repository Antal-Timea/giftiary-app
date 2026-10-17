package app.giftiary.presentation.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.giftiary.MainActivity
import app.giftiary.data.auth.AUTH_CALLBACK_URL
import app.giftiary.domain.model.InviteToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Join links as the system hands them over and as users paste them, and how the token is held. */
@RunWith(RobolectricTestRunner::class)
class WishlistJoinLinkTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val token = InviteToken.of(TOKEN)!!

    @Test
    fun `the link carries the token in its fragment`() {
        assertEquals("giftiary://wishlist/join#$TOKEN", joinLink(token))
    }

    @Test
    fun `an opened join link yields its token`() {
        assertEquals(PendingJoin.Invite(token), pendingJoinOf(view(joinLink(token))))
        assertEquals(PendingJoin.Invite(token), pendingJoinOf(view("GIFTIARY://Wishlist/join#$TOKEN")))
    }

    @Test
    fun `a join link without a usable token is an invalid join, not ignored`() {
        listOf(
            "giftiary://wishlist/join",
            "giftiary://wishlist/join#",
            "giftiary://wishlist/join#tooShort",
            "giftiary://wishlist/join#${TOKEN}x",
            "giftiary://wishlist/join#${TOKEN.dropLast(1)}=",
        ).forEach { link -> assertEquals(link, PendingJoin.InvalidLink, pendingJoinOf(view(link))) }
    }

    @Test
    fun `other links, the auth callback among them, are not joins`() {
        listOf(
            "$AUTH_CALLBACK_URL?code=one-time",
            "giftiary://auth/join#$TOKEN",
            "giftiary://other/join#$TOKEN",
            "giftiary://wishlist/other#$TOKEN",
            "giftiary://wishlist/join?token=$TOKEN",
            "gifty://wishlist/join#$TOKEN",
            "https://wishlist/join#$TOKEN",
        ).forEach { link -> assertNull(link, pendingJoinOf(view(link))) }
        assertNull(pendingJoinOf(Intent(Intent.ACTION_MAIN)))
        assertNull(pendingJoinOf(Intent(Intent.ACTION_SEND, Uri.parse(joinLink(token)))))
    }

    @Test
    fun `the system opens join links and the auth callback in the app, nothing else of the scheme`() {
        assertEquals(listOf(MainActivity::class.java.name), handlersOf(joinLink(token)))
        assertEquals(listOf(MainActivity::class.java.name), handlersOf("$AUTH_CALLBACK_URL?code=one-time"))
        assertTrue(handlersOf("giftiary://wishlist/elsewhere#$TOKEN").isEmpty())
        assertTrue(handlersOf("giftiary://other/join#$TOKEN").isEmpty())
    }

    @Test
    fun `a pasted link, message or bare token yields the token`() {
        assertEquals(token, inviteTokenOfPastedText(joinLink(token)))
        assertEquals(token, inviteTokenOfPastedText("  $TOKEN\n"))
        assertEquals(token, inviteTokenOfPastedText("Join my wishlist “Birthday” on Giftiary: ${joinLink(token)}"))
        assertEquals(token, inviteTokenOfPastedText("Giftiary://WISHLIST/join#$TOKEN."))
    }

    @Test
    fun `anything else pasted yields nothing`() {
        listOf(
            "",
            "hello",
            TOKEN.dropLast(1),
            "${TOKEN}A",
            "giftiary://wishlist/join#${TOKEN}A",
            "giftiary://auth/callback#$TOKEN",
            "https://example.com/#$TOKEN",
        ).forEach { text -> assertNull(text, inviteTokenOfPastedText(text)) }
    }

    @Test
    fun `a token never shows itself in text`() {
        assertFalse(token.toString().contains(TOKEN))
        assertFalse(PendingJoin.Invite(token).toString().contains(TOKEN))
        assertFalse(JoinWishlistUiState(input = joinLink(token)).toString().contains(TOKEN))
    }

    @Test
    fun `a waiting join is handed out once, and a newer link replaces it`() = runTest {
        val pending = PendingWishlistJoin()
        assertFalse(pending.isPending.first())

        pending.offer(PendingJoin.InvalidLink)
        pending.offer(PendingJoin.Invite(token))
        assertTrue(pending.isPending.first())

        assertEquals(PendingJoin.Invite(token), pending.take())
        assertNull(pending.take())
        assertFalse(pending.isPending.first())
    }

    private fun view(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))

    private fun handlersOf(link: String): List<String> {
        val intent = view(link).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
    }

    private companion object {
        const val TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE"
    }
}
