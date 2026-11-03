package app.giftiary.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.giftiary.IncomingLink
import app.giftiary.MainActivity
import app.giftiary.incomingLinkOf
import app.giftiary.presentation.sharing.pendingJoinOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Recovery links apart from confirmation and join links, as parsed and as the system routes them. */
@RunWith(RobolectricTestRunner::class)
class RecoveryLinkTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the recovery URL is its own path on the auth host`() {
        assertEquals("giftiary://auth/recovery", AUTH_RECOVERY_URL)
        assertTrue(AUTH_RECOVERY_URL != AUTH_CALLBACK_URL)
    }

    @Test
    fun `a recovery link yields its code`() {
        val link = recoveryLinkOf(view("giftiary://auth/recovery?code=abc-123"))

        assertTrue(link is RecoveryLink.Code)
        assertEquals("abc-123", (link as RecoveryLink.Code).value)
    }

    @Test
    fun `a recovery link without a usable code is invalid, not ignored`() {
        listOf(
            "giftiary://auth/recovery",
            "giftiary://auth/recovery?code=",
            "giftiary://auth/recovery?code=%20",
            "giftiary://auth/recovery?other=1",
            "giftiary://auth/recovery?code=abc#access_token=x",
        ).forEach { link -> assertEquals(link, RecoveryLink.Invalid, recoveryLinkOf(view(link))) }
    }

    @Test
    fun `confirmation, join and other links are not recovery links`() {
        listOf(
            "giftiary://auth/callback?code=abc",
            "giftiary://wishlist/join#AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE",
            "https://giftiary.app/join/#AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE",
            "giftiary://other/recovery?code=abc",
            "gifty://auth/recovery?code=abc",
            "giftiary://auth/recovery/extra?code=abc",
        ).forEach { link -> assertNull(link, recoveryLinkOf(view(link))) }
        assertNull(recoveryLinkOf(Intent(Intent.ACTION_SEND, Uri.parse("giftiary://auth/recovery?code=abc"))))
    }

    @Test
    fun `the app routes recovery before the generic auth callback, and confirmations to it`() {
        assertTrue(incomingLinkOf(view("giftiary://auth/recovery?code=abc")) is IncomingLink.Recovery)
        assertTrue(incomingLinkOf(view("giftiary://auth/recovery")) == IncomingLink.Recovery(RecoveryLink.Invalid))
        assertEquals(IncomingLink.Other, incomingLinkOf(view("giftiary://auth/callback?code=abc")))
        assertTrue(incomingLinkOf(view("giftiary://wishlist/join#AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE")) is IncomingLink.Join)
        assertTrue(incomingLinkOf(view("https://giftiary.app/join/#AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE")) is IncomingLink.Join)
        assertNull(pendingJoinOf(view("giftiary://auth/recovery?code=abc")))
    }

    @Test
    fun `the system opens recovery and confirmation links in the app, other auth paths nowhere`() {
        assertEquals(listOf(MainActivity::class.java.name), handlersOf("giftiary://auth/recovery?code=abc"))
        assertEquals(listOf(MainActivity::class.java.name), handlersOf("giftiary://auth/callback?code=abc"))
        assertTrue(handlersOf("giftiary://auth/elsewhere?code=abc").isEmpty())
    }

    private fun view(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))

    private fun handlersOf(link: String): List<String> {
        val intent = view(link).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
    }
}
