package app.gifty.data.remote

import app.gifty.data.remote.RemoteTestData.alice
import app.gifty.data.remote.RemoteTestData.token
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The failure contract shared by reads and writes, for what the SDK may throw besides HTTP errors. */
@RunWith(RobolectricTestRunner::class)
class RemoteCallsTest {

    private val engine = MockEngine { error("No request is expected") }
    private val clients = AccountBoundClients("https://test.supabase.co", "sb_publishable_test-key", engine)
    private val account = RemoteAccount.capture(alice, token(alice))

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun `an unexpected exception is a server failure, with its cause kept`() = runTest {
        val unexpected = IllegalStateException("SDK state")

        val failure = runCatching { clients.call(account) { throw unexpected } }.exceptionOrNull()

        assertTrue("got $failure", failure is RemoteException)
        assertEquals(RemoteFailure.SERVER, (failure as RemoteException).failure)
        assertSame(unexpected, failure.cause)
    }

    @Test
    fun `a cancellation passes through as itself, never as a failure`() = runTest {
        // CancellationException is an IllegalStateException; it must not be caught as one.
        val cancellation = CancellationException("cancelled")

        val failure = runCatching { clients.call(account) { throw cancellation } }.exceptionOrNull()

        assertSame(cancellation, failure)
    }
}
