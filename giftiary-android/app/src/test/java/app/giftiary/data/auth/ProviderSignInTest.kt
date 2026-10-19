package app.giftiary.data.auth

import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.testing.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderSignInTest {

    private val auth = FakeAuthRepository()

    private fun signIn(vararg enabled: AuthProvider) = ProviderSignIn(auth, EnabledAuthProviders(enabled.toSet()))

    @Test
    fun `only providers this build enables are offered, in a fixed order`() {
        assertEquals(emptyList<AuthProvider>(), signIn().providers)
        assertEquals(listOf(AuthProvider.FACEBOOK), signIn(AuthProvider.FACEBOOK).providers)
        assertEquals(AuthProvider.entries, signIn(AuthProvider.FACEBOOK, AuthProvider.GOOGLE).providers)
    }

    @Test
    fun `a provider that is not enabled never starts`() {
        val signIn = signIn(AuthProvider.GOOGLE)

        assertNull(signIn.begin(AuthProvider.FACEBOOK))
        assertEquals(ProviderSignInState.Idle, signIn.state.value)
        assertEquals(emptyList<AuthProvider>(), auth.providerUrlCalls)
    }

    @Test
    fun `beginning opens the provider's page once, however often it is tapped`() {
        val signIn = signIn(AuthProvider.GOOGLE, AuthProvider.FACEBOOK)

        assertNotNull(signIn.begin(AuthProvider.GOOGLE))
        assertNull(signIn.begin(AuthProvider.GOOGLE))
        assertNull(signIn.begin(AuthProvider.FACEBOOK))

        assertEquals(ProviderSignInState.Launching(AuthProvider.GOOGLE), signIn.state.value)
        assertEquals(listOf(AuthProvider.GOOGLE), auth.providerUrlCalls)
    }

    @Test
    fun `closing the tab without signing in ends the sign-in quietly, and it can start again`() {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.begin(AuthProvider.GOOGLE)

        signIn.onAppResumed()

        assertEquals(ProviderSignInState.Idle, signIn.state.value)
        assertNotNull(signIn.begin(AuthProvider.GOOGLE))
    }

    @Test
    fun `resuming changes nothing unless a page was open`() {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.onAppResumed()
        assertEquals(ProviderSignInState.Idle, signIn.state.value)

        signIn.begin(AuthProvider.GOOGLE)
        signIn.onExchangeStarted()
        signIn.onAppResumed()
        assertEquals(ProviderSignInState.Exchanging, signIn.state.value)
    }

    @Test
    fun `without a browser the sign-in fails with that reason`() {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.begin(AuthProvider.GOOGLE)

        signIn.onBrowserMissing()

        assertEquals(ProviderSignInState.Failed(AuthError.NO_BROWSER), signIn.state.value)
    }

    @Test
    fun `a page that cannot be built is a failure, and nothing opens`() {
        auth.providerUrlFailure = IllegalStateException("simulated")
        val signIn = signIn(AuthProvider.GOOGLE)

        assertNull(signIn.begin(AuthProvider.GOOGLE))
        assertEquals(ProviderSignInState.Failed(AuthError.PROVIDER_FAILED), signIn.state.value)
    }

    @Test
    fun `a failure stays until the next attempt, which starts normally`() {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.begin(AuthProvider.GOOGLE)
        signIn.onCallbackFailed(AuthError.CANCELLED)
        signIn.onAppResumed()
        assertEquals(ProviderSignInState.Failed(AuthError.CANCELLED), signIn.state.value)

        assertNotNull(signIn.begin(AuthProvider.GOOGLE))
        assertEquals(ProviderSignInState.Launching(AuthProvider.GOOGLE), signIn.state.value)
    }

    @Test
    fun `a callback error is shown only when a provider sign-in waited for it`() {
        val signIn = signIn(AuthProvider.GOOGLE)

        // E.g. an expired sign-up confirmation link: signing in normally still works.
        signIn.onCallbackFailed(AuthError.PROVIDER_FAILED)

        assertEquals(ProviderSignInState.Idle, signIn.state.value)
    }

    @Test
    fun `a successful exchange ends the sign-in`() {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.begin(AuthProvider.GOOGLE)

        val exchange = signIn.onExchangeStarted()
        assertEquals(ProviderSignInState.Exchanging, signIn.state.value)
        assertNull("nothing starts while exchanging", signIn.begin(AuthProvider.GOOGLE))
        signIn.onExchangeFinished(exchange, null)

        assertEquals(ProviderSignInState.Idle, signIn.state.value)
    }

    @Test
    fun `a failed exchange of a provider sign-in is reported, worth retrying or as a provider failure`() {
        listOf(
            AuthError.NETWORK_UNAVAILABLE to AuthError.NETWORK_UNAVAILABLE,
            AuthError.RATE_LIMITED to AuthError.RATE_LIMITED,
            AuthError.INVALID_CREDENTIALS to AuthError.PROVIDER_FAILED,
            AuthError.UNKNOWN to AuthError.PROVIDER_FAILED,
        ).forEach { (error, shown) ->
            val signIn = signIn(AuthProvider.GOOGLE)
            signIn.begin(AuthProvider.GOOGLE)
            signIn.onExchangeFinished(signIn.onExchangeStarted(), error)
            assertEquals("$error", ProviderSignInState.Failed(shown), signIn.state.value)
        }
    }

    @Test
    fun `a failed exchange no provider sign-in waited for shows nothing`() {
        val signIn = signIn(AuthProvider.GOOGLE)

        signIn.onExchangeFinished(signIn.onExchangeStarted(), AuthError.UNKNOWN)

        assertEquals(ProviderSignInState.Idle, signIn.state.value)
    }

    @Test
    fun `only the latest exchange decides, and it keeps whether a provider waited`() {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.begin(AuthProvider.GOOGLE)
        val first = signIn.onExchangeStarted()
        // The same link opened twice.
        val second = signIn.onExchangeStarted()

        signIn.onExchangeFinished(first, null)
        assertEquals(ProviderSignInState.Exchanging, signIn.state.value)

        signIn.onExchangeFinished(second, AuthError.UNKNOWN)
        assertEquals(ProviderSignInState.Failed(AuthError.PROVIDER_FAILED), signIn.state.value)
    }
}
