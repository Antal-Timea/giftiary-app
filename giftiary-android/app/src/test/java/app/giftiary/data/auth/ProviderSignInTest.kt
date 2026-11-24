package app.giftiary.data.auth

import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.data.remote.RemoteFailure
import app.giftiary.testing.FakeAuthRepository
import app.giftiary.testing.FakeFeatureFlagRemote
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSignInTest {

    private val auth = FakeAuthRepository()

    // Facebook Login is switched on remotely unless a test says otherwise.
    private val flags = FakeFeatureFlagRemote(ProviderSignIn.FACEBOOK_LOGIN_FLAG)

    private fun signIn(vararg enabled: AuthProvider) = ProviderSignIn(auth, EnabledAuthProviders(enabled.toSet()), flags)

    @Test
    fun `only providers this build enables are offered, in a fixed order`() = runTest {
        val none = signIn().apply { refreshProviders() }
        val facebook = signIn(AuthProvider.FACEBOOK).apply { refreshProviders() }
        val both = signIn(AuthProvider.FACEBOOK, AuthProvider.GOOGLE).apply { refreshProviders() }

        assertEquals(emptyList<AuthProvider>(), none.providers.value)
        assertEquals(listOf(AuthProvider.FACEBOOK), facebook.providers.value)
        assertEquals(AuthProvider.entries, both.providers.value)
    }

    @Test
    fun `Facebook is offered only once its remote flag has been read as on`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE, AuthProvider.FACEBOOK)
        assertEquals(listOf(AuthProvider.GOOGLE), signIn.providers.value)
        assertNull(signIn.begin(AuthProvider.FACEBOOK))

        signIn.refreshProviders()

        assertEquals(listOf(ProviderSignIn.FACEBOOK_LOGIN_FLAG), flags.reads)
        assertEquals(AuthProvider.entries, signIn.providers.value)
        assertNotNull(signIn.begin(AuthProvider.FACEBOOK))
    }

    @Test
    fun `a flag that is off or missing hides Facebook and leaves Google as it was`() = runTest {
        flags.enabled.clear()
        val signIn = signIn(AuthProvider.GOOGLE, AuthProvider.FACEBOOK)

        signIn.refreshProviders()

        assertEquals(listOf(AuthProvider.GOOGLE), signIn.providers.value)
        assertNull(signIn.begin(AuthProvider.FACEBOOK))
        assertEquals(emptyList<AuthProvider>(), auth.providerUrlCalls)
        assertNotNull(signIn.begin(AuthProvider.GOOGLE))
    }

    @Test
    fun `switching the flag off withdraws Facebook at the next read, and a failed read counts as off`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE, AuthProvider.FACEBOOK)
        signIn.refreshProviders()
        assertEquals(AuthProvider.entries, signIn.providers.value)

        flags.enabled.clear()
        signIn.refreshProviders()
        assertEquals(listOf(AuthProvider.GOOGLE), signIn.providers.value)

        flags.enabled += ProviderSignIn.FACEBOOK_LOGIN_FLAG
        signIn.refreshProviders()
        flags.failure = RemoteFailure.NETWORK
        signIn.refreshProviders()
        assertEquals(listOf(AuthProvider.GOOGLE), signIn.providers.value)
    }

    @Test
    fun `while the flag is being read, Google is offered and can start`() = runTest(UnconfinedTestDispatcher()) {
        flags.answer = CompletableDeferred()
        val signIn = signIn(AuthProvider.GOOGLE, AuthProvider.FACEBOOK)
        val reading = async { signIn.refreshProviders() }

        assertEquals(listOf(AuthProvider.GOOGLE), signIn.providers.value)
        assertNotNull(signIn.begin(AuthProvider.GOOGLE))

        flags.answer!!.complete(Unit)
        reading.await()
        assertEquals(AuthProvider.entries, signIn.providers.value)
    }

    @Test
    fun `an older flag read that finishes last does not overwrite the newer one`() = runTest(UnconfinedTestDispatcher()) {
        flags.holdEach = true
        val signIn = signIn(AuthProvider.GOOGLE, AuthProvider.FACEBOOK)
        val older = async { signIn.refreshProviders() }
        val newer = async { signIn.refreshProviders() }
        val (olderAnswer, newerAnswer) = flags.held.toList()

        newerAnswer.complete(false)
        newer.await()
        olderAnswer.complete(true)
        older.await()
        assertEquals(listOf(AuthProvider.GOOGLE), signIn.providers.value)
        assertNull(signIn.begin(AuthProvider.FACEBOOK))

        // And the other way round: a stale "off" cannot withdraw a newer "on".
        val third = async { signIn.refreshProviders() }
        val fourth = async { signIn.refreshProviders() }
        val (thirdAnswer, fourthAnswer) = flags.held.drop(2)
        fourthAnswer.complete(true)
        fourth.await()
        thirdAnswer.complete(false)
        third.await()
        assertEquals(AuthProvider.entries, signIn.providers.value)
    }

    @Test
    fun `a new read hides Facebook until it answers, even if the last read was on`() = runTest(UnconfinedTestDispatcher()) {
        val signIn = signIn(AuthProvider.GOOGLE, AuthProvider.FACEBOOK)
        signIn.refreshProviders()
        assertEquals(AuthProvider.entries, signIn.providers.value)

        flags.holdEach = true
        val reading = async { signIn.refreshProviders() }
        assertEquals(listOf(AuthProvider.GOOGLE), signIn.providers.value)
        assertNull(signIn.begin(AuthProvider.FACEBOOK))
        assertNotNull(signIn.begin(AuthProvider.GOOGLE))

        flags.held.single().complete(true)
        reading.await()
        assertEquals(AuthProvider.entries, signIn.providers.value)
    }

    @Test
    fun `a build without Facebook never reads the flag`() = runTest {
        signIn(AuthProvider.GOOGLE).refreshProviders()

        assertEquals(emptyList<String>(), flags.reads)
    }

    @Test
    fun `a provider that is not enabled never starts`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE)

        assertNull(signIn.begin(AuthProvider.FACEBOOK))
        assertEquals(ProviderSignInState.Idle, signIn.state.value)
        assertEquals(emptyList<AuthProvider>(), auth.providerUrlCalls)
    }

    @Test
    fun `beginning opens the provider's page once, however often it is tapped`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE, AuthProvider.FACEBOOK)
        signIn.refreshProviders()

        assertNotNull(signIn.begin(AuthProvider.GOOGLE))
        assertNull(signIn.begin(AuthProvider.GOOGLE))
        assertNull(signIn.begin(AuthProvider.FACEBOOK))

        assertEquals(ProviderSignInState.Launching(AuthProvider.GOOGLE), signIn.state.value)
        assertEquals(listOf(AuthProvider.GOOGLE), auth.providerUrlCalls)
    }

    @Test
    fun `closing the tab without signing in ends the sign-in quietly, and it can start again`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.begin(AuthProvider.GOOGLE)

        signIn.onAppResumed()

        assertEquals(ProviderSignInState.Idle, signIn.state.value)
        assertNotNull(signIn.begin(AuthProvider.GOOGLE))
    }

    @Test
    fun `resuming changes nothing unless a page was open`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.onAppResumed()
        assertEquals(ProviderSignInState.Idle, signIn.state.value)

        signIn.begin(AuthProvider.GOOGLE)
        signIn.onExchangeStarted()
        signIn.onAppResumed()
        assertEquals(ProviderSignInState.Exchanging, signIn.state.value)
    }

    @Test
    fun `without a browser the sign-in fails with that reason`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.begin(AuthProvider.GOOGLE)

        signIn.onBrowserMissing()

        assertEquals(ProviderSignInState.Failed(AuthError.NO_BROWSER), signIn.state.value)
    }

    @Test
    fun `a page that cannot be built is a failure, and nothing opens`() = runTest {
        auth.providerUrlFailure = IllegalStateException("simulated")
        val signIn = signIn(AuthProvider.GOOGLE)

        assertNull(signIn.begin(AuthProvider.GOOGLE))
        assertEquals(ProviderSignInState.Failed(AuthError.PROVIDER_FAILED), signIn.state.value)
    }

    @Test
    fun `a page that cannot be reached is a network failure, worth retrying`() = runTest {
        auth.providerUrlFailure = AuthException(AuthError.NETWORK_UNAVAILABLE)
        val signIn = signIn(AuthProvider.GOOGLE)

        assertNull(signIn.begin(AuthProvider.GOOGLE))
        assertEquals(ProviderSignInState.Failed(AuthError.NETWORK_UNAVAILABLE), signIn.state.value)
    }

    @Test
    fun `while the page is being found the sign-in is under way, and a second tap waits for nothing`() = runTest {
        val gate = CompletableDeferred<Unit>().also { auth.providerUrlGate = it }
        val signIn = signIn(AuthProvider.GOOGLE, AuthProvider.FACEBOOK)
        signIn.refreshProviders()

        val first = async(UnconfinedTestDispatcher(testScheduler)) { signIn.begin(AuthProvider.GOOGLE) }
        assertEquals(ProviderSignInState.Launching(AuthProvider.GOOGLE), signIn.state.value)
        assertNull(signIn.begin(AuthProvider.FACEBOOK))

        gate.complete(Unit)
        assertNotNull(first.await())
        assertEquals(listOf(AuthProvider.GOOGLE), auth.providerUrlCalls)
    }

    @Test
    fun `a page found after the attempt ended opens nothing`() = runTest {
        val gate = CompletableDeferred<Unit>().also { auth.providerUrlGate = it }
        val signIn = signIn(AuthProvider.GOOGLE)
        val first = async(UnconfinedTestDispatcher(testScheduler)) { signIn.begin(AuthProvider.GOOGLE) }

        // E.g. the user left and came back while the page was being found.
        signIn.onAppResumed()
        gate.complete(Unit)

        assertNull(first.await())
        assertEquals(ProviderSignInState.Idle, signIn.state.value)
    }

    @Test
    fun `a stale attempt's page is dropped, also when a newer attempt is under way`() = runTest {
        val gate = CompletableDeferred<Unit>().also { auth.providerUrlGate = it }
        val signIn = signIn(AuthProvider.GOOGLE)
        val first = async(UnconfinedTestDispatcher(testScheduler)) { signIn.begin(AuthProvider.GOOGLE) }
        signIn.onAppResumed()
        val second = async(UnconfinedTestDispatcher(testScheduler)) { signIn.begin(AuthProvider.GOOGLE) }

        gate.complete(Unit)

        assertNull(first.await())
        assertNotNull(second.await())
        assertEquals(ProviderSignInState.Launching(AuthProvider.GOOGLE), signIn.state.value)
    }

    @Test
    fun `a failure stays until the next attempt, which starts normally`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.begin(AuthProvider.GOOGLE)
        signIn.onCallbackFailed(AuthError.CANCELLED)
        signIn.onAppResumed()
        assertEquals(ProviderSignInState.Failed(AuthError.CANCELLED), signIn.state.value)

        assertNotNull(signIn.begin(AuthProvider.GOOGLE))
        assertEquals(ProviderSignInState.Launching(AuthProvider.GOOGLE), signIn.state.value)
    }

    @Test
    fun `a callback error is shown only when a provider sign-in waited for it`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE)

        // E.g. an expired sign-up confirmation link: signing in normally still works.
        signIn.onCallbackFailed(AuthError.PROVIDER_FAILED)

        assertEquals(ProviderSignInState.Idle, signIn.state.value)
    }

    @Test
    fun `a successful exchange ends the sign-in`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE)
        signIn.begin(AuthProvider.GOOGLE)

        val exchange = signIn.onExchangeStarted()
        assertEquals(ProviderSignInState.Exchanging, signIn.state.value)
        assertNull("nothing starts while exchanging", signIn.begin(AuthProvider.GOOGLE))
        signIn.onExchangeFinished(exchange, null)

        assertEquals(ProviderSignInState.Idle, signIn.state.value)
    }

    @Test
    fun `a failed exchange of a provider sign-in is reported, worth retrying or as a provider failure`() = runTest {
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
    fun `a failed exchange no provider sign-in waited for shows nothing`() = runTest {
        val signIn = signIn(AuthProvider.GOOGLE)

        signIn.onExchangeFinished(signIn.onExchangeStarted(), AuthError.UNKNOWN)

        assertEquals(ProviderSignInState.Idle, signIn.state.value)
    }

    @Test
    fun `only the latest exchange decides, and it keeps whether a provider waited`() = runTest {
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
