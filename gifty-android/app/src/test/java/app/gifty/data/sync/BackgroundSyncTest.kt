package app.gifty.data.sync

import app.gifty.data.auth.BackgroundSessionResult
import app.gifty.domain.model.AuthUser
import app.gifty.domain.repository.OwnerSessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** When a background job syncs, and when it asks to run again: the table the Worker returns by. */
class BackgroundSyncTest {

    private val ready = OwnerSessionState.Ready(AuthUser(UUID.randomUUID(), "anna@example.com"))
    private val state = MutableStateFlow<OwnerSessionState>(ready)
    private var session = BackgroundSessionResult.READY
    private val drains = mutableListOf<Boolean>()
    private var outcomes = ArrayDeque<DrainOutcome>()
    private var drain: suspend (Boolean) -> DrainOutcome = { full ->
        drains += full
        outcomes.removeFirstOrNull() ?: DrainOutcome.DONE
    }
    private val sync = BackgroundSync(state, { session }, { drain(it) })

    @Test
    fun `a finished drain ends the job, and only a periodic job pulls`() = runTest {
        assertFalse(sync.run(full = true, attempt = 0))
        assertFalse(sync.run(full = false, attempt = 0))

        assertEquals(listOf(true, false), drains)
    }

    @Test
    fun `only transient trouble is retried`() = runTest {
        DrainOutcome.entries.forEach { outcome ->
            drains.clear()
            outcomes = ArrayDeque(listOf(outcome))
            val again = sync.run(full = false, attempt = 0)
            assertEquals("$outcome", outcome == DrainOutcome.TRANSIENT, again)
        }
    }

    @Test
    fun `retries stop once the job has run five times before`() = runTest {
        val again = (0..6).map { attempt ->
            outcomes = ArrayDeque(listOf(DrainOutcome.TRANSIENT))
            sync.run(full = false, attempt = attempt)
        }

        assertEquals(listOf(true, true, true, true, true, false, false), again)
    }

    @Test
    fun `a drain that leaves more to push at once is followed by at most two more, push only`() = runTest {
        outcomes = ArrayDeque(List(5) { DrainOutcome.DEFERRED })

        assertFalse(sync.run(full = true, attempt = 0))

        assertEquals(listOf(true, false, false), drains)
    }

    @Test
    fun `a deferred drain followed by trouble is retried`() = runTest {
        outcomes = ArrayDeque(listOf(DrainOutcome.DEFERRED, DrainOutcome.TRANSIENT))

        assertTrue(sync.run(full = false, attempt = 0))
        assertEquals(2, drains.size)
    }

    @Test
    fun `the session decides whether to sync at all`() = runTest {
        mapOf(
            BackgroundSessionResult.NO_SESSION to false,
            BackgroundSessionResult.REJECTED to false,
            BackgroundSessionResult.RETRY_LATER to true,
        ).forEach { (result, expected) ->
            session = result
            assertEquals("$result", expected, sync.run(full = true, attempt = 0))
        }
        assertTrue("nothing synced without a usable session", drains.isEmpty())
        session = BackgroundSessionResult.RETRY_LATER
        assertFalse("not retried forever", sync.run(full = true, attempt = BackgroundSync.RETRY_LIMIT))
    }

    @Test
    fun `nothing is synced unless an account is open`() = runTest {
        listOf(OwnerSessionState.SignedOut, OwnerSessionState.Failed(ready.user)).forEach {
            state.value = it
            assertFalse("$it", sync.run(full = true, attempt = 0))
        }
        assertTrue(drains.isEmpty())
    }

    @Test
    fun `the job waits for the account to open, and tries again later if it does not`() = runTest {
        state.value = OwnerSessionState.Restoring
        val opening = async { sync.run(full = true, attempt = 0) }
        advanceTimeBy(30.seconds)
        state.value = ready
        assertFalse(opening.await())
        assertEquals(1, drains.size)

        state.value = OwnerSessionState.Restoring
        assertTrue(sync.run(full = true, attempt = 0))
        assertEquals(1, drains.size)
    }

    @Test
    fun `a drain that never answers counts as transient, and the job gives up waiting`() = runTest {
        val never = CompletableDeferred<DrainOutcome>()
        drain = { never.await() }
        val running = async { sync.run(full = false, attempt = 0) }

        advanceTimeBy(5.minutes + 1.seconds)

        assertTrue(running.await())
        assertFalse("the drain itself is not cancelled by giving up on it", never.isCancelled)
    }
}
