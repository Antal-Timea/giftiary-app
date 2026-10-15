package app.gifty.data.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.gifty.domain.model.AuthUser
import app.gifty.domain.model.NotificationAccess
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.UpcomingOccasion
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.repository.OwnerSessionState
import app.gifty.testing.FakeOccasionRepository
import app.gifty.testing.MutableClock
import app.gifty.testing.occasion
import app.gifty.testing.person
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * One reminder heartbeat, with the real per-account preferences and the account, time, access,
 * occasions and system post as seams. A posted digest is recorded as (owner, days until each).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReminderDigestTest {

    private val budapest = ZoneId.of("Europe/Budapest")
    private val alice = AuthUser(UUID.randomUUID(), "alice@example.com")
    private val bob = AuthUser(UUID.randomUUID(), "bob@example.com")
    private val preferences = ReminderPreferences(ApplicationProvider.getApplicationContext<Context>())
    private val account = MutableStateFlow<OwnerSessionState>(OwnerSessionState.Ready(alice))
    private val clock = MutableClock(Instant.parse("2026-05-10T08:00:00Z")) // 10:00 in Budapest
    private var zone: ZoneId = budapest
    private var access = NotificationAccess.GRANTED
    private val occasions = FakeOccasionRepository()
    private val anna = person("Anna")
    private val posts = mutableListOf<Pair<UUID, List<Int>>>()
    private val cancelled = mutableListOf<UUID>()

    /** What the system post does; by default it accepts the digest. */
    private var onPost: (UUID) -> Boolean = { true }

    init {
        preferences.setEnabled(alice.id, true)
        occasions.snapshotOwner = alice.id
        occasions.people.value = mapOf(anna.id to anna)
        occasions.givenOccasions(
            occasion(anna.id, on(LocalDate.of(2026, 5, 10))),
            occasion(anna.id, on(LocalDate.of(2026, 5, 17), OccasionType.NAME_DAY)),
            occasion(anna.id, on(LocalDate.of(2026, 5, 12), OccasionType.ANNIVERSARY)),
        )
    }

    // Cold start and account

    @Test
    fun `in a cold process the digest waits for the account to open`() = runTest {
        account.value = OwnerSessionState.Restoring
        val running = async { digest().run(attempt = 0) }
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(posts.isEmpty())

        account.value = OwnerSessionState.Ready(alice)

        assertFalse(running.await())
        assertEquals(listOf(alice.id to listOf(0, 7)), posts)
        assertEquals(LocalDate.of(2026, 5, 10), preferences.lastDigest(alice.id))
    }

    @Test
    fun `an account that does not open within a minute is tried again a few times, then left to the next heartbeat`() = runTest {
        account.value = OwnerSessionState.Restoring

        assertTrue(digest().run(attempt = 0))
        assertTrue(digest().run(attempt = 2))
        assertFalse(digest().run(attempt = 3))
        assertTrue(posts.isEmpty())
    }

    @Test
    fun `nothing is shown when signed out, when the account failed to open, or when reminders are off`() = runTest {
        account.value = OwnerSessionState.SignedOut
        assertFalse(digest().run(0))
        account.value = OwnerSessionState.Failed(alice)
        assertFalse(digest().run(0))
        account.value = OwnerSessionState.Ready(alice)
        preferences.setEnabled(alice.id, false)
        assertFalse(digest().run(0))

        assertTrue(posts.isEmpty())
        assertNull(preferences.lastDigest(alice.id))
    }

    // When

    @Test
    fun `a digest is only shown between 09_00 and 21_00 local time`() = runTest {
        assertPostsAt("2026-05-10T06:59:59Z", expected = false) // 08:59:59
        assertPostsAt("2026-05-10T07:00:00Z", expected = true) // 09:00
        assertPostsAt("2026-05-10T18:59:59Z", expected = true) // 20:59:59
        assertPostsAt("2026-05-10T19:00:00Z", expected = false) // 21:00
    }

    @Test
    fun `09_00 is local time on days the clocks change`() = runTest {
        // 29 March: clocks go forward at 02:00, so 09:00 is 07:00 UTC.
        assertPostsAt("2026-03-29T06:59:00Z", expected = false)
        assertPostsAt("2026-03-29T07:00:00Z", expected = true)
        // 25 October: clocks go back at 03:00, so 09:00 is 08:00 UTC.
        assertPostsAt("2026-10-25T07:30:00Z", expected = false)
        assertPostsAt("2026-10-25T08:00:00Z", expected = true)
    }

    // Once a day

    @Test
    fun `a day is reminded once, however many heartbeats come`() = runTest {
        repeat(3) { digest().run(0) }

        assertEquals(1, posts.size)
    }

    @Test
    fun `a new local date is a new day, and a time zone further west does not make a reminded day new again`() = runTest {
        digest().run(0) // 10:00 on 10 May in Budapest
        occasions.givenOccasions(occasion(anna.id, on(LocalDate.of(2026, 5, 10))), occasion(anna.id, on(LocalDate.of(2026, 5, 11))))

        zone = ZoneId.of("Asia/Tokyo")
        clock.instant = Instant.parse("2026-05-11T01:00:00Z") // 10:00 on 11 May in Tokyo: a new day
        digest().run(0)
        zone = ZoneId.of("America/Los_Angeles")
        clock.instant = Instant.parse("2026-05-11T02:00:00Z") // 19:00 on 10 May in Los Angeles: stepped back
        digest().run(0)

        assertEquals(2, posts.size)
        assertEquals(LocalDate.of(2026, 5, 11), preferences.lastDigest(alice.id))
    }

    // A day only counts once a digest was accepted

    @Test
    fun `without permission nothing is shown and the day stays open until it is given`() = runTest {
        access = NotificationAccess.PERMISSION_MISSING
        digest().run(0)
        access = NotificationAccess.BLOCKED
        digest().run(0)
        assertTrue(posts.isEmpty())
        assertNull(preferences.lastDigest(alice.id))

        access = NotificationAccess.GRANTED
        digest().run(0)

        assertEquals(1, posts.size)
    }

    @Test
    fun `a digest the system refuses does not count as shown`() = runTest {
        onPost = { false }
        digest().run(0)
        assertNull(preferences.lastDigest(alice.id))

        onPost = { true }
        digest().run(0)

        assertEquals(LocalDate.of(2026, 5, 10), preferences.lastDigest(alice.id))
    }

    @Test
    fun `with nothing due nothing is shown, and an occasion added later the same day still is`() = runTest {
        occasions.givenOccasions(occasion(anna.id, on(LocalDate.of(2026, 5, 12))))
        digest().run(0)
        assertTrue(posts.isEmpty())
        assertNull(preferences.lastDigest(alice.id))

        occasions.givenOccasions(occasion(anna.id, on(LocalDate.of(2026, 5, 17))))
        digest().run(0)

        assertEquals(listOf(alice.id to listOf(7)), posts)
    }

    @Test
    fun `a failed read is tried again a few times and leaves the day open`() = runTest {
        occasions.readFailure = IOException("disk")

        assertTrue(digest().run(attempt = 0))
        assertFalse(digest().run(attempt = 3))
        assertTrue(posts.isEmpty())
        assertNull(preferences.lastDigest(alice.id))
    }

    // Accounts

    @Test
    fun `occasions read for another account than the open one are never shown`() = runTest {
        occasions.snapshotOwner = bob.id

        digest().run(0)

        assertTrue(posts.isEmpty())
    }

    @Test
    fun `a digest posted just as the account closes is taken back and not counted`() = runTest {
        onPost = {
            account.value = OwnerSessionState.Ready(bob)
            true
        }

        digest().run(0)

        assertEquals(listOf(alice.id), posts.map { it.first })
        assertEquals(listOf(alice.id), cancelled)
        assertNull(preferences.lastDigest(alice.id))
    }

    @Test
    fun `each account has its own choice and its own day`() = runTest {
        digest().run(0)
        account.value = OwnerSessionState.Ready(bob)
        occasions.snapshotOwner = bob.id

        digest().run(0)
        assertEquals("bob did not turn reminders on", 1, posts.size)
        preferences.setEnabled(bob.id, true)
        digest().run(0)

        assertEquals(listOf(alice.id, bob.id), posts.map { it.first })
    }

    // Process death

    @Test
    fun `stopped before posting, nothing is shown or counted, and the next heartbeat shows it`() = runTest {
        val read = occasions.readFailure
        occasions.readFailure = CancellationException("stopped")
        runCatching { digest().run(0) }
        assertTrue(posts.isEmpty())
        assertNull(preferences.lastDigest(alice.id))

        occasions.readFailure = read
        digest().run(0)

        assertEquals(1, posts.size)
    }

    @Test
    fun `stopped after posting but before counting, the next heartbeat posts the same digest again`() = runTest {
        onPost = { throw CancellationException("stopped right after the system took it") }
        runCatching { digest().run(0) }
        assertNull(preferences.lastDigest(alice.id))

        onPost = { true }
        digest().run(0)

        // Both into alice's one slot: the notifier replaces, quietly, rather than adding a second.
        assertEquals(listOf(alice.id, alice.id), posts.map { it.first })
        assertEquals(LocalDate.of(2026, 5, 10), preferences.lastDigest(alice.id))
    }

    @Test
    fun `once counted, a later heartbeat after a restart shows nothing`() = runTest {
        digest().run(0)

        // A new process: new objects reading the same stored preferences.
        val restarted = ReminderPreferences(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(LocalDate.of(2026, 5, 10), restarted.lastDigest(alice.id))
        digest().run(0)

        assertEquals(1, posts.size)
    }

    private suspend fun assertPostsAt(instant: String, expected: Boolean) {
        clock.instant = Instant.parse(instant)
        preferences.setLastDigest(alice.id, LocalDate.of(2000, 1, 1))
        val date = clock.instant.atZone(zone).toLocalDate()
        occasions.givenOccasions(occasion(anna.id, on(date)))
        posts.clear()

        digest().run(0)

        assertEquals("at $instant", expected, posts.isNotEmpty())
    }

    private fun digest() = ReminderDigest(
        accountState = account,
        preferences = preferences,
        clock = clock,
        zone = zone,
        access = { access },
        snapshot = occasions::getOccasionSnapshot,
        post = { owner, due: List<UpcomingOccasion> ->
            posts += owner to due.map { it.daysUntil }
            onPost(owner)
        },
        cancel = { cancelled += it },
    )

    private fun on(date: LocalDate, type: OccasionType = OccasionType.BIRTHDAY) =
        OccasionDetails(type, null, date.dayOfMonth, date.monthValue, null, true)
}
