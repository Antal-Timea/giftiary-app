package app.giftiary.data.auth

import android.content.Intent
import android.net.Uri
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.testing.FakeAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthCallbackHandlerTest {

    private val auth = FakeAuthRepository().apply { state.value = AuthState.SignedOut }
    private val providerSignIn = ProviderSignIn(auth, EnabledAuthProviders(setOf(AuthProvider.GOOGLE)))

    private fun TestScope.handler() =
        AuthCallbackHandler(auth, providerSignIn, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `a provider's callback code signs the account in and ends the sign-in`() = runTest {
        providerSignIn.begin(AuthProvider.GOOGLE)

        assertTrue(handler().handle(view("giftiary://auth/callback?code=one-time")))

        assertEquals(listOf("one-time"), auth.completeCalls)
        assertEquals(AuthState.SignedIn(auth.callbackUser), auth.state.value)
        assertEquals(ProviderSignInState.Idle, providerSignIn.state.value)
    }

    @Test
    fun `the exchange shows while it runs`() = runTest {
        providerSignIn.begin(AuthProvider.GOOGLE)
        val gate = CompletableDeferred<Unit>().also { auth.gate = it }

        handler().handle(view("giftiary://auth/callback?code=one-time"))
        assertEquals(ProviderSignInState.Exchanging, providerSignIn.state.value)

        gate.complete(Unit)
        assertEquals(ProviderSignInState.Idle, providerSignIn.state.value)
    }

    @Test
    fun `a failed exchange of a provider sign-in is reported`() = runTest {
        providerSignIn.begin(AuthProvider.GOOGLE)
        auth.completeFailure = AuthError.NETWORK_UNAVAILABLE

        handler().handle(view("giftiary://auth/callback?code=one-time"))

        assertEquals(ProviderSignInState.Failed(AuthError.NETWORK_UNAVAILABLE), providerSignIn.state.value)
        assertEquals(AuthState.SignedOut, auth.state.value)
    }

    @Test
    fun `a sign-up confirmation is exchanged too, and its failure stays quiet`() = runTest {
        auth.completeFailure = AuthError.UNKNOWN

        assertTrue(handler().handle(view("giftiary://auth/callback?code=one-time")))

        assertEquals(listOf("one-time"), auth.completeCalls)
        assertEquals(ProviderSignInState.Idle, providerSignIn.state.value)
    }

    @Test
    fun `a callback error ends the provider sign-in without an exchange`() = runTest {
        providerSignIn.begin(AuthProvider.GOOGLE)

        assertTrue(handler().handle(view("giftiary://auth/callback?error=access_denied")))

        assertTrue(auth.completeCalls.isEmpty())
        assertEquals(ProviderSignInState.Failed(AuthError.CANCELLED), providerSignIn.state.value)
    }

    @Test
    fun `other links are left alone`() = runTest {
        val handler = handler()

        assertFalse(handler.handle(view("giftiary://auth/recovery?code=one-time")))
        assertFalse(handler.handle(view("giftiary://wishlist/join#AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE")))
        assertFalse(handler.handle(Intent(Intent.ACTION_MAIN)))
        assertTrue(auth.completeCalls.isEmpty())
    }

    private fun view(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))
}
