package app.giftiary.data.auth

import android.content.Intent
import android.net.Uri
import app.giftiary.IncomingLink
import app.giftiary.domain.repository.AuthError
import app.giftiary.incomingLinkOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Callback links of confirmed sign-ups and provider sign-ins, apart from recovery and join links. */
@RunWith(RobolectricTestRunner::class)
class AuthCallbackTest {

    @Test
    fun `a callback with a code yields the code, never in its text`() {
        val callback = authCallbackOf(view("giftiary://auth/callback?code=one-time-code"))

        assertEquals("one-time-code", (callback as AuthCallback.Code).value)
        assertFalse("one-time-code" in callback.toString())
    }

    @Test
    fun `a turned-down consent is a cancel, every other reported error a failure`() {
        assertEquals(
            AuthCallback.Failed(AuthError.CANCELLED),
            authCallbackOf(view("giftiary://auth/callback?error=access_denied&error_description=denied")),
        )
        assertEquals(
            AuthCallback.Failed(AuthError.CANCELLED),
            authCallbackOf(view("giftiary://auth/callback?error=access_denied&error_code=access_denied")),
        )
        listOf(
            // An expired email link is reported as access_denied too, but with a code of its own.
            "giftiary://auth/callback?error=access_denied&error_code=otp_expired",
            "giftiary://auth/callback?error=server_error&error_description=oops",
            "giftiary://auth/callback?error_code=bad_oauth_state",
            // An error wins over a code next to it.
            "giftiary://auth/callback?code=abc&error=server_error",
        ).forEach { link ->
            assertEquals(link, AuthCallback.Failed(AuthError.PROVIDER_FAILED), authCallbackOf(view(link)))
        }
    }

    @Test
    fun `a callback without a usable code or error has nothing to do`() {
        assertEquals(AuthCallback.Empty, authCallbackOf(view("giftiary://auth/callback")))
        assertEquals(AuthCallback.Empty, authCallbackOf(view("giftiary://auth/callback?code=")))
    }

    @Test
    fun `recovery, join and foreign links are not callbacks`() {
        listOf(
            "giftiary://auth/recovery?code=abc",
            "giftiary://auth/elsewhere?code=abc",
            "giftiary://wishlist/join#AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE",
            "gifty://auth/callback?code=abc",
            "https://auth/callback?code=abc",
        ).forEach { link -> assertNull(link, authCallbackOf(view(link))) }
        assertNull(authCallbackOf(Intent(Intent.ACTION_MAIN, Uri.parse("giftiary://auth/callback?code=abc"))))
        assertNull(authCallbackOf(Intent(Intent.ACTION_VIEW)))
    }

    @Test
    fun `provider callbacks go to the auth callback, while recovery and join links keep their own way`() {
        assertEquals(IncomingLink.Other, incomingLinkOf(view("giftiary://auth/callback?code=abc")))
        assertEquals(IncomingLink.Other, incomingLinkOf(view("giftiary://auth/callback?error=access_denied")))
        // An error next to a recovery code still opens the recovery flow, never the callback.
        assertTrue(incomingLinkOf(view("giftiary://auth/recovery?code=abc&error=server_error")) is IncomingLink.Recovery)
        assertTrue(incomingLinkOf(view("giftiary://wishlist/join#AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE")) is IncomingLink.Join)
    }

    private fun view(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))
}
