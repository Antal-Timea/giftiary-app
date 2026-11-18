package app.giftiary.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.auth.BackgroundSessionResult
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.SyncReconciler
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperationEntity
import app.giftiary.data.photos.GiftPhotoFiles
import app.giftiary.data.photos.GiftPhotoSync
import app.giftiary.data.remote.RemoteAccount
import app.giftiary.data.remote.RemoteAccounts
import app.giftiary.data.remote.RemoteException
import app.giftiary.data.remote.RemoteFailure
import app.giftiary.data.remote.RemoteTestData.token
import app.giftiary.data.remote.dto.RemoteGiftFields
import app.giftiary.data.remote.dto.RemotePersonFields
import app.giftiary.data.remote.toRemoteFields
import app.giftiary.data.repository.GiftRepositoryImpl
import app.giftiary.data.repository.PersonRepositoryImpl
import app.giftiary.data.sync.FakeBackend.Behavior
import app.giftiary.data.user.ActiveOwner
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.FakeGiftPhotoRemote
import app.giftiary.testing.FakeSyncScheduler
import app.giftiary.testing.MutableClock
import app.giftiary.testing.blockCurrentMutation
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CyclicBarrier

/**
 * Foreground orchestration with real Room, [SyncRunner], [SyncPusher] and supabase-kt [Auth], and
 * [FakeBackend] as the backend. Room and the coordinator run on the test scheduler, so
 * `advanceUntilIdle()` waits for every drain without sleeping; [FakeBackend.Behavior.Hold] stops one mid-push.
 * Runs are counted from the coordinator's own log lines.
 */
@OptIn(ExperimentalCoroutinesApi::class, SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class ForegroundSyncCoordinatorTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val dispatcher = StandardTestDispatcher()

    // The application scope. Not the test's background scope: advanceUntilIdle() does not wait
    // for background work, and every drain must finish before a test looks at the result.
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())
    private val clock = MutableClock(Instant.parse("2026-10-11T12:00:00.123Z"))
    private val activeOwner = FakeActiveOwner(null)
    private val auth: Auth = createSupabaseClient("https://example.supabase.co", "publishable-key") {
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
        }
    }.auth
    private lateinit var database: GiftiaryDatabase
    private val photoRemote = FakeGiftPhotoRemote()
    private lateinit var photoSync: GiftPhotoSync
    private lateinit var backend: FakeBackend
    private lateinit var runner: SyncRunner
    private lateinit var puller: SyncPuller
    private lateinit var people: PersonRepositoryImpl
    private val scheduler = FakeSyncScheduler()

    private val operations get() = database.syncOperationDao()

    @Before
    fun setUp() {
        ShadowLog.reset()
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftiaryDatabase::class.java)
            // Room work runs on the test scheduler, which runs on the test's (main) thread.
            .setQueryCoroutineContext(dispatcher)
            .allowMainThreadQueries()
            .build()
        // Writes made here never ask for sync, like a process that died right after committing.
        people = PersonRepositoryImpl(
            database, database.personDao(), database.occasionDao(), SyncOperationQueue(database.syncOperationDao(), clock),
            activeOwner, clock, SyncRequester {},
            database.photoTransferDao(), GiftPhotoFiles(ApplicationProvider.getApplicationContext()),
        )
        backend = FakeBackend(database)
        photoSync = GiftPhotoSync(
            database.photoTransferDao(), database.giftDao(), database.giftIdeaDao(), database.personDao(), database.wishDao(), database.syncOperationDao(),
            GiftPhotoFiles(ApplicationProvider.getApplicationContext()), photoRemote, clock,
        )
        runner = SyncRunner(
            database.syncOperationDao(),
            SyncDependencies(database.personDao(), database.occasionDao(), database.giftIdeaDao(), database.giftDao(), database.wishlistDao(), database.wishDao()),
            newPusher(),
            activeOwner,
            photoSync,
        )
        puller = SyncPuller(
            database, backend, database.syncOperationDao(), database.personDao(), database.occasionDao(),
            database.giftIdeaDao(), database.giftDao(), database.wishlistDao(), database.wishDao(), activeOwner,
        )
    }

    @After
    fun tearDown() {
        assertFalse("a request was made inside a Room transaction", backend.requestInsideTransaction)
        database.close()
    }

    // ---- requests ----

    @Test
    fun `one request starts one drain`() = test {
        val coordinator = startIdle(owner = alice)
        val anna = addPerson("Anna", alice)

        coordinator.requestSync()
        advanceUntilIdle()

        assertEquals(listOf("Run 1: pushed 1, settled 1, stopped null, needs attention false"), runs())
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
    }

    @Test
    fun `rapid requests share the waiting consumer and one slot`() = test {
        val coordinator = startIdle(owner = alice)
        addPerson("Anna", alice)

        repeat(5) { coordinator.requestSync() }
        advanceUntilIdle()

        // The first request goes straight to the waiting consumer; the other four share the slot.
        assertEquals(listOf("Run 1: pushed 1", "Run 1: pushed 0"), runs().map { it.substringBefore(",") })
        assertEquals(1, backend.writes.size)
    }

    @Test
    fun `a request during a run waits for its drain, then starts exactly one more`() = test {
        val coordinator = startIdle(owner = alice)
        val anna = addPerson("Anna", alice)
        val hold = hold()

        coordinator.requestSync()
        advanceUntilIdle()
        assertTrue(hold.arrived.isCompleted)
        repeat(3) { coordinator.requestSync() }
        advanceUntilIdle()
        assertTrue("nothing runs beside the held drain", runs().isEmpty())

        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("Run 1: pushed 1", "Run 1: pushed 0"), runs().map { it.substringBefore(",") })
        assertTrue(logs().none { "ALREADY_RUNNING" in it })
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
    }

    @Test
    fun `a request during a drain's last run is not lost`() = test {
        val coordinator = startIdle(owner = alice)
        repeat(101) { addPerson("Person $it", alice) }
        repeat(100) { backend.script += Behavior.Normal }
        val hold = hold()

        coordinator.requestSync()
        advanceUntilIdle()
        assertEquals("held in run 2", 1, runs().size)
        coordinator.requestSync()
        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("Run 1: pushed 100", "Run 2: pushed 1", "Run 1: pushed 0"), runs().map { it.substringBefore(",") })
    }

    @Test
    fun `starting twice still leaves a single consumer`() = test {
        addPerson("Anna", alice)
        importSession(alice)
        activeOwner.ownerId.value = alice
        val hold = hold()
        val coordinator = newCoordinator(appScope)
        coordinator.start()
        coordinator.start()
        advanceUntilIdle()
        assertTrue(hold.arrived.isCompleted)

        coordinator.requestSync()
        advanceUntilIdle()

        // A second consumer would take this request now, find the runner busy and log that run.
        assertTrue(runs().isEmpty())
        hold.release.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("Run 1: pushed 1", "Run 1: pushed 0"), runs().map { it.substringBefore(",") })
        assertTrue(logs().none { "ALREADY_RUNNING" in it })
    }

    @Test
    fun `concurrent starts still leave a single consumer`() = test {
        addPerson("Anna", alice)
        importSession(alice)
        activeOwner.ownerId.value = alice
        val hold = hold()
        val coordinator = newCoordinator(appScope)
        val together = CyclicBarrier(8)

        List(8) { thread { together.await(); coordinator.start() } }.forEach { it.join() }
        advanceUntilIdle()
        assertTrue(hold.arrived.isCompleted)
        coordinator.requestSync()
        advanceUntilIdle()

        // A second consumer would take this request now, find the runner busy and log that run.
        assertTrue(runs().isEmpty())
        hold.release.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("Run 1: pushed 1", "Run 1: pushed 0"), runs().map { it.substringBefore(",") })
        assertTrue(logs().none { "ALREADY_RUNNING" in it })
    }

    @Test
    fun `a request made before start waits for the consumer`() = test {
        // An open account whose opening was never reported, and no session: neither the owner nor
        // the token collector can ask for sync, and a drain shows only as a failed capture.
        val quiet = ForegroundSyncCoordinator(UnreportedOwner(alice), RemoteAccounts(auth), runner, puller, scheduler, photoSync, appScope)
        quiet.start()
        advanceUntilIdle()
        assertTrue("starting alone asks for nothing", logs().isEmpty())

        val coordinator = ForegroundSyncCoordinator(UnreportedOwner(alice), RemoteAccounts(auth), runner, puller, scheduler, photoSync, appScope)
        coordinator.requestSync()
        advanceUntilIdle()
        assertTrue("nothing drains before start", logs().isEmpty())

        coordinator.start()
        advanceUntilIdle()

        assertEquals("one drain, for the earlier request", listOf("No account to sync: UNAUTHENTICATED"), logs())
    }

    // ---- how many runs a drain makes ----

    @Test
    fun `105 operations take a drain's two runs`() = test {
        repeat(105) { addPerson("Person $it", alice) }

        start(owner = alice)

        assertEquals(listOf("Run 1: pushed 100, settled 100", "Run 2: pushed 5, settled 5"), runs().map { it.substringBefore(", stopped") })
        assertTrue(operations.getAllForOwner(alice).isEmpty())
    }

    @Test
    fun `a drain stops after two runs, and the rest waits for the next request`() = test {
        repeat(205) { addPerson("Person $it", alice) }

        val coordinator = start(owner = alice)

        assertEquals(listOf("Run 1: pushed 100", "Run 2: pushed 100"), runs().map { it.substringBefore(",") })
        assertEquals(200, backend.writes.size)
        assertEquals(5, operations.getAllForOwner(alice).size)

        coordinator.requestSync()
        advanceUntilIdle()

        assertEquals("Run 1: pushed 5", runs().last().substringBefore(","))
        assertTrue(operations.getAllForOwner(alice).isEmpty())
    }

    @Test
    fun `a newer change left by the first run is pushed by the second`() = test {
        val coordinator = startIdle(owner = alice, secret = "t1")
        val anna = addPerson("Anna", alice)
        val hold = hold()
        coordinator.requestSync()
        advanceUntilIdle()

        // M2 is made while M1 is on its way, and the token is refreshed meanwhile.
        people.updatePerson(anna.id, "Anna K.", null)
        importSession(alice, "t2")
        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(true, false), backend.writes.map { it.insert })
        assertEquals("each run captured the token current when it started", listOf(token(alice, "t1"), token(alice, "t2")), backend.writes.map { it.token })
        assertEquals("Run 1: pushed 1, settled 1", runs()[0].substringBefore(", stopped"))
        assertEquals("Run 2: pushed 1, settled 1", runs()[1].substringBefore(", stopped"))
        assertTrue(operations.getAllForOwner(alice).isEmpty())
    }

    // ---- failures that may pass later wait for the next request ----

    @Test
    fun `a lost request is pushed once, and the queue writes it makes start nothing`() = test {
        val anna = addPerson("Anna", alice)
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)

        start(owner = alice)

        assertEquals(listOf("Run 1: pushed 1, settled 0, stopped null, needs attention false"), runs())
        assertEquals("the push's own two sends", 2, backend.writes.size)
        val operation = pending(anna)
        assertEquals(operation.mutationId, operation.inFlightMutationId)
    }

    @Test
    fun `a server failure is pushed once`() = test {
        addPerson("Anna", alice)
        backend.script += Behavior.Fail(RemoteFailure.SERVER)

        start(owner = alice)

        assertEquals(listOf("Run 1: pushed 1, settled 0, stopped null, needs attention false"), runs())
    }

    @Test
    fun `an outcome that cannot be known yet is pushed once`() = test {
        // The update matches nothing, yet the backend shows an older version.
        val anna = syncedPerson("Anna", version = 3)
        backend.rows.getValue(anna).version = 2
        activeOwner.ownerId.value = alice
        people.updatePerson(anna, "Anna K.", null)

        start(owner = alice)

        assertEquals(listOf("Run 1: pushed 1, settled 0, stopped null, needs attention false"), runs())
        assertNull(pending(anna).blockedReason)
    }

    @Test
    fun `refused values that an older copy may still overtake are pushed once and reported`() = test {
        val anna = addPerson("Anna", alice)
        // An earlier attempt left the create in flight with its outcome unknown.
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)
        newPusher().push(RemoteAccount.capture(alice, token(alice)), pending(anna).id)
        backend.script += Behavior.Fail(RemoteFailure.CONSTRAINT_VIOLATION)

        start(owner = alice)

        assertEquals(listOf("Run 1: pushed 1, settled 0, stopped null, needs attention true"), runs())
        assertNull(pending(anna).blockedReason)
    }

    @Test
    fun `a run that settled something but left a failure gets one more run, never a third`() = test {
        addPerson("Anna", alice)
        val bela = addPerson("Béla", alice)
        backend.script += listOf(Behavior.Normal, Behavior.LoseRequest, Behavior.LoseRequest, Behavior.LoseRequest)

        val coordinator = start(owner = alice)

        assertEquals(listOf("Run 1: pushed 2, settled 1", "Run 2: pushed 1, settled 0"), runs().map { it.substringBefore(", stopped") })
        assertEquals("two sends, then one resend of the same mutation", 3, backend.writes.count { it.entityId == bela.id })

        // Only another request tries again.
        advanceUntilIdle()
        assertEquals(2, runs().size)
        coordinator.requestSync()
        advanceUntilIdle()
        assertEquals(3, runs().size)
        assertEquals(RemoteState.SYNCED, remoteStateOf(bela))
    }

    // ---- runs that stop ----

    @Test
    fun `a busy runner ends the drain without waiting or retrying`() = test {
        val coordinator = startIdle(owner = alice)
        addPerson("Anna", alice)
        val hold = hold()
        val other = async { runner.run(RemoteAccount.capture(alice, token(alice))) }
        advanceUntilIdle()
        assertTrue(hold.arrived.isCompleted)

        coordinator.requestSync()
        advanceUntilIdle()
        assertEquals(listOf("Run 1: pushed 0, settled 0, stopped ALREADY_RUNNING, needs attention false"), runs())

        hold.release.complete(Unit)
        other.await()
        advanceUntilIdle()
        assertEquals(1, runs().size)
    }

    @Test
    fun `a session that cannot be used ends the drain`() = test {
        addPerson("Anna", alice)
        addPerson("Béla", alice)
        backend.script += Behavior.Fail(RemoteFailure.UNAUTHENTICATED)

        start(owner = alice)

        assertEquals(listOf("Run 1: pushed 1, settled 0, stopped SESSION_REQUIRED, needs attention false"), runs())
        assertEquals(1, backend.writes.size)
    }

    @Test
    fun `a new access token tries again after the session was refused`() = test {
        val anna = addPerson("Anna", alice)
        backend.script += Behavior.Fail(RemoteFailure.UNAUTHENTICATED)
        start(owner = alice, secret = "expired")
        assertEquals(1, runs().size)

        importSession(alice, "refreshed")
        advanceUntilIdle()

        assertEquals(2, runs().size)
        assertEquals(token(alice, "refreshed"), backend.writes.last().token)
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
    }

    @Test
    fun `refused permission ends the drain`() = test {
        addPerson("Anna", alice)
        addPerson("Béla", alice)
        backend.script += Behavior.Fail(RemoteFailure.FORBIDDEN)

        start(owner = alice)

        assertEquals(listOf("Run 1: pushed 1, settled 0, stopped PERMISSION, needs attention false"), runs())
        assertEquals(1, backend.writes.size)
    }

    // ---- accounts ----

    @Test
    fun `nothing is pushed while no account is ready, e g restoring or signed out`() = test {
        addPerson("Anna", alice)
        // Restoring: a stored session may exist before the account's data is open.
        val coordinator = start(owner = null)
        coordinator.requestSync()
        advanceUntilIdle()
        // Signed out: no session at all.
        auth.clearSession()
        coordinator.requestSync()
        advanceUntilIdle()

        assertTrue(runs().isEmpty())
        assertTrue(backend.writes.isEmpty())
        assertEquals(1, operations.getAllForOwner(alice).size)
    }

    @Test
    fun `an account becoming ready asks for sync`() = test {
        val anna = addPerson("Anna", alice)
        start(owner = null)
        assertTrue(runs().isEmpty())

        activeOwner.ownerId.value = alice
        advanceUntilIdle()

        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
    }

    @Test
    fun `an account already ready when the coordinator starts is not missed`() = test {
        val anna = addPerson("Anna", alice)

        start(owner = alice)

        assertEquals(1, runs().size)
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
    }

    @Test
    fun `signing out during a push lets it finish as its account, then nothing more runs`() = test {
        val anna = addPerson("Anna", alice)
        val bela = addPerson("Béla", alice)
        val hold = hold()
        val coordinator = start(owner = alice)
        assertTrue("the first drain waits in Anna's push", hold.arrived.isCompleted)

        activeOwner.ownerId.value = null
        auth.clearSession()
        coordinator.requestSync()
        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("Run 1: pushed 1, settled 1, stopped OWNER_CHANGED, needs attention false"), runs())
        assertEquals(listOf(token(alice)), backend.writes.map { it.token })
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
        assertNotNull("left for Alice's next session", pending(bela))
    }

    @Test
    fun `an account switch stops the old account's drain, and a pending request runs for the new one`() = test {
        addPerson("Anna", alice)
        val bela = addPerson("Béla", alice)
        val bobsFriend = addPerson("Cecil", bob)
        val hold = hold()
        val coordinator = start(owner = alice)
        assertTrue("the first drain waits in Anna's push", hold.arrived.isCompleted)

        // A request made while Alice is active, then Alice's data closes and Bob's opens.
        coordinator.requestSync()
        activeOwner.ownerId.value = null
        importSession(bob)
        activeOwner.ownerId.value = bob
        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("Run 1: pushed 1, settled 1, stopped OWNER_CHANGED, needs attention false", runs().first())
        assertEquals(listOf(alice, bob), backend.writes.map { it.owner })
        assertEquals(token(bob), backend.writes.last().token)
        assertEquals(bobsFriend.id, backend.writes.last().entityId)
        assertNotNull(pending(bela))
    }

    @Test
    fun `an account switch during a drain's last push ends that drain, and the new account gets its own`() = test {
        val anna = addPerson("Anna", alice)
        val cecil = addPerson("Cecil", bob)
        val hold = hold()
        start(owner = alice)
        assertTrue("run 1 waits in Anna's push, its only candidate", hold.arrived.isCompleted)

        // Alice leaves M2 behind, then Bob's data opens, all while that last push is in flight.
        people.updatePerson(anna.id, "Anna K.", null)
        activeOwner.ownerId.value = null
        importSession(bob)
        activeOwner.ownerId.value = bob
        advanceUntilIdle()
        hold.release.complete(Unit)
        advanceUntilIdle()

        // Alice's run did not see the switch, and its result would allow a second run.
        assertEquals("Run 1: pushed 1, settled 1, stopped null, needs attention false", runs().first())
        assertTrue("Bob's work is never a second run of Alice's drain", runs().none { it.startsWith("Run 2") })
        assertEquals("Run 1: pushed 1, settled 1, stopped null, needs attention false", runs()[1])
        assertEquals(2, runs().size)
        assertEquals(listOf(anna.id to token(alice), cecil.id to token(bob)), backend.writes.map { it.entityId to it.token })
        assertNotNull("Alice's M2 waits for her next drain", pending(anna).mutationId)
    }

    @Test
    fun `an active account whose session belongs to another account is not synced`() = test {
        addPerson("Anna", alice)
        importSession(bob)

        start(owner = alice, secret = null)

        assertTrue(runs().isEmpty())
        assertTrue(backend.writes.isEmpty())
        assertTrue(logs().contains("No account to sync: ACCOUNT_MISMATCH"))
    }

    // ---- after a restart ----

    @Test
    fun `work committed before the process died is pushed when the account is ready again`() = test {
        // Committed without any request, as if the process died right after.
        val anna = addPerson("Anna", alice)
        val bela = addPerson("Béla", alice)

        start(owner = null)
        activeOwner.ownerId.value = alice
        advanceUntilIdle()

        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
        assertEquals(RemoteState.SYNCED, remoteStateOf(bela))
    }

    @Test
    fun `a mutation left in flight by an earlier process is settled from evidence after a restart`() = test {
        val anna = addPerson("Anna", alice)
        // The create committed, but neither its answer nor any evidence arrived.
        backend.readFailure = RemoteFailure.NETWORK
        backend.script += listOf(Behavior.LoseResponse, Behavior.LoseResponse)
        newPusher().push(RemoteAccount.capture(alice, token(alice)), pending(anna).id)
        assertNotNull(pending(anna).inFlightMutationId)
        val sends = backend.writes.size
        backend.readFailure = null

        start(owner = null)
        activeOwner.ownerId.value = alice
        advanceUntilIdle()

        assertEquals("settled without sending again", sends, backend.writes.size)
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
        assertTrue(operations.getAllForOwner(alice).isEmpty())
    }

    // ---- failures of the coordinator itself ----

    @Test
    fun `an unexpected exception ends the drain, is logged, and leaves the queue to the next request`() = test {
        val anna = addPerson("Anna", alice)
        backend.script += Behavior.Crash(IllegalStateException("broken invariant"))

        val coordinator = start(owner = alice)

        assertTrue(runs().isEmpty())
        assertTrue(logs().contains("Sync drain failed: IllegalStateException"))
        assertEquals("not retried by itself", 1, backend.writes.size)
        val operation = pending(anna)
        assertNull(operation.blockedReason)
        assertNull("not taken for a network failure", operation.lastErrorCode)

        coordinator.requestSync()
        advanceUntilIdle()

        assertEquals(1, runs().size)
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
    }

    @Test
    fun `cancelling the scope stops the consumer instead of being caught`() = test {
        val scope = CoroutineScope(appScope.coroutineContext + Job())
        val coordinator = newCoordinator(scope)
        val anna = addPerson("Anna", alice)
        importSession(alice)
        activeOwner.ownerId.value = alice
        val hold = hold()
        coordinator.start()
        advanceUntilIdle()
        assertTrue(hold.arrived.isCompleted)

        scope.cancel()
        advanceUntilIdle()
        coordinator.requestSync()
        advanceUntilIdle()

        assertTrue(runs().isEmpty())
        assertTrue(logs().none { it.startsWith("Sync drain failed") })
        assertEquals(1, backend.writes.size)
        assertNotNull("left in flight, to be resolved by a later push", pending(anna).inFlightMutationId)
    }

    @Test
    fun `logs carry counts and reasons only, never tokens, ids or names`() = test {
        val coordinator = startIdle(owner = alice, secret = "secret-one")
        val anna = addPerson("Anna", alice)
        val hold = hold()
        coordinator.requestSync()
        advanceUntilIdle()
        people.updatePerson(anna.id, "Anna K.", null)
        importSession(alice, "secret-two")
        hold.release.complete(Unit)
        advanceUntilIdle()
        backend.script += Behavior.Crash(IllegalStateException("${anna.id}"))
        people.updatePerson(anna.id, "Anna Kovács", null)
        coordinator.requestSync()
        advanceUntilIdle()

        val logs = logs()
        assertTrue(logs.size >= 3)
        val secrets = listOf("secret-one", "secret-two", token(alice, "secret-one"), alice.toString(), anna.id.toString(), "Anna") +
            backend.writes.map { it.mutationId.toString() }
        logs.forEach { line -> secrets.forEach { secret -> assertFalse("'$line' leaks '$secret'", secret in line) } }
    }

    // ---- full sync: push, then pull ----

    @Test
    fun `a local change asks only for a push`() = test {
        val coordinator = startIdle(owner = alice)
        addPerson("Anna", alice)

        coordinator.requestSync()
        advanceUntilIdle()

        assertEquals(1, runs().size)
        assertEquals(0, pulls())
    }

    @Test
    fun `the account becoming ready pushes, then pulls`() = test {
        val anna = addPerson("Anna", alice)
        val bela = remotePerson("Béla")

        start(owner = alice)

        assertEquals(listOf("Run 1", "Pull"), drainSteps())
        assertEquals(1, pulls())
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
        assertNotNull(database.personDao().findById(bela, alice))
    }

    @Test
    fun `a full sync, as on coming to the foreground, pushes the local change and then pulls the backend's`() = test {
        val coordinator = startIdle(owner = alice)
        val anna = addPerson("Anna", alice)
        val bela = remotePerson("Béla")

        coordinator.requestFullSync()
        advanceUntilIdle()

        assertEquals(listOf("Run 1", "Pull"), drainSteps())
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
        assertNotNull(database.personDao().findById(bela, alice))
    }

    @Test
    fun `a full sync with nothing to push still pulls`() = test {
        val coordinator = startIdle(owner = alice)
        val bela = remotePerson("Béla")

        coordinator.requestFullSync()
        advanceUntilIdle()

        assertEquals("Run 1: pushed 0", runs().single().substringBefore(","))
        assertEquals(1, pulls())
        assertNotNull(database.personDao().findById(bela, alice))
    }

    @Test
    fun `blocked work does not stop the pull, which leaves it and its queue row exactly as they are`() = test {
        val anna = addPerson("Anna", alice)
        val create = pending(anna)
        operations.blockCurrentMutation(create.id, create.mutationId, BlockReason.REJECTED.name)
        backend.seed(anna.id, alice, 1, UUID.randomUUID(), RemotePersonFields("Not Anna", null, null, null, null))
        val bela = remotePerson("Béla")
        val before = database.personDao().findById(anna.id, alice)
        val queue = operations.getAllForOwner(alice)

        start(owner = alice)

        assertEquals(1, pulls())
        assertTrue(logs().any { it.startsWith("Pull: inserted 1,") && "skipped queued 1" in it })
        assertEquals(before, database.personDao().findById(anna.id, alice))
        assertEquals(queue, operations.getAllForOwner(alice))
        assertNotNull(database.personDao().findById(bela, alice))
    }

    @Test
    fun `a push whose request was lost is followed by one pull`() = test {
        assertUnfinishedPushStillPulls(listOf(Behavior.LoseRequest, Behavior.LoseRequest))
    }

    @Test
    fun `a push the server failed is followed by one pull`() = test {
        assertUnfinishedPushStillPulls(listOf(Behavior.Fail(RemoteFailure.SERVER)))
    }

    @Test
    fun `a refused session stops before the pull, and the pull follows once a new token is there`() = test {
        val anna = addPerson("Anna", alice)
        backend.script += Behavior.Fail(RemoteFailure.UNAUTHENTICATED)
        val bela = remotePerson("Béla")

        start(owner = alice, secret = "expired")

        assertEquals(0, pulls())
        assertNull(database.personDao().findById(bela, alice))

        // A new token asks only for a push, but the full sync it could not finish is still wanted.
        importSession(alice, "refreshed")
        advanceUntilIdle()

        assertEquals(1, pulls())
        assertEquals(listOf(token(alice, "refreshed")), backend.fetchTokens.distinct())
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
        assertNotNull(database.personDao().findById(bela, alice))
    }

    @Test
    fun `refused permission stops before the pull`() = test {
        addPerson("Anna", alice)
        backend.script += Behavior.Fail(RemoteFailure.FORBIDDEN)

        start(owner = alice)

        assertTrue(runs().single(), "stopped PERMISSION" in runs().single())
        assertEquals(0, pulls())
    }

    @Test
    fun `an account switch during the push leaves the old account unpulled, and the new one gets its own full sync`() = test {
        addPerson("Anna", alice)
        val cecil = addPerson("Cecil", bob)
        val hold = hold()
        start(owner = alice)
        assertTrue(hold.arrived.isCompleted)

        activeOwner.ownerId.value = null
        importSession(bob)
        activeOwner.ownerId.value = bob
        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("Bob's drain only", 1, pulls())
        assertEquals(listOf(token(bob)), backend.fetchTokens.distinct())
        assertEquals(listOf("Run 1", "Run 1", "Pull"), drainSteps())
        assertEquals(cecil.id, backend.writes.last().entityId)
    }

    @Test
    fun `a new token alone asks only for a push`() = test {
        startIdle(owner = alice, secret = "t1")
        addPerson("Anna", alice)

        importSession(alice, "t2")
        advanceUntilIdle()

        assertEquals(1, runs().size)
        assertEquals(0, pulls())
    }

    @Test
    fun `signing out during the push leaves the old account unpulled, even with its token still there`() = test {
        val anna = addPerson("Anna", alice)
        val hold = hold()
        start(owner = alice)
        assertTrue(hold.arrived.isCompleted)

        // The account's data closes first; its session is ended only afterwards.
        activeOwner.ownerId.value = null
        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
        assertEquals("not even read", 0, pulls())
    }

    @Test
    fun `a token refreshed during the push is the one the pull uses`() = test {
        val coordinator = startIdle(owner = alice, secret = "t1")
        addPerson("Anna", alice)
        val hold = hold()
        coordinator.requestFullSync()
        advanceUntilIdle()

        importSession(alice, "t2")
        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(token(alice, "t1"), backend.writes.single().token)
        assertEquals(listOf(token(alice, "t2")), backend.fetchTokens.distinct())
        assertEquals(1, pulls())
    }

    @Test
    fun `an account switch during the pull applies nothing and ends the drain`() = test {
        val coordinator = startIdle(owner = alice)
        val bela = remotePerson("Béla")
        backend.beforeFetch = { table -> if (table == "people") activeOwner.ownerId.value = null }

        coordinator.requestFullSync()
        advanceUntilIdle()

        assertTrue(logs().contains("Pull: owner changed"))
        assertNull(database.personDao().findById(bela, alice))
        assertEquals(1, pulls())
    }

    @Test
    fun `a local change made during the pull is left alone by it and pushed afterwards`() = test {
        val anna = addPerson("Anna", alice)
        val coordinator = startIdle(owner = alice)
        var edited = false
        backend.afterFetch = { table ->
            if (table == "people" && !edited) {
                edited = true
                people.updatePerson(anna.id, "Anna K.", null)
                coordinator.requestSync()
            }
        }

        coordinator.requestFullSync()
        advanceUntilIdle()

        assertTrue(logs().any { it.startsWith("Pull:") && "skipped queued 1" in it })
        assertEquals(listOf("Run 1", "Pull", "Run 1"), drainSteps())
        assertEquals("Anna K.", database.personDao().findById(anna.id, alice)!!.name)
        assertEquals(anna.id, backend.writes.last().entityId)
        assertTrue(operations.getAllForOwner(alice).isEmpty())
    }

    @Test
    fun `a full sync asked for during a push-only drain is done by that drain`() = test {
        val coordinator = startIdle(owner = alice)
        addPerson("Anna", alice)
        val hold = hold()
        coordinator.requestSync()
        advanceUntilIdle()
        assertTrue(hold.arrived.isCompleted)

        coordinator.requestFullSync()
        hold.release.complete(Unit)
        advanceUntilIdle()

        // The full sync's own wake-up then finds nothing left to do.
        assertEquals(listOf("Run 1", "Pull", "Run 1"), drainSteps())
        assertEquals(1, pulls())
    }

    @Test
    fun `a push request after a full sync request, both before start, still pulls`() = test {
        // Only these requests can start a drain: the owner's opening is never reported.
        val coordinator = ForegroundSyncCoordinator(UnreportedOwner(alice), RemoteAccounts(auth), runner, puller, scheduler, photoSync, appScope)
        importSession(alice)
        activeOwner.ownerId.value = alice
        val bela = remotePerson("Béla")
        coordinator.requestFullSync()
        coordinator.requestSync()

        coordinator.start()
        advanceUntilIdle()

        assertEquals(1, pulls())
        assertNotNull(database.personDao().findById(bela, alice))
    }

    @Test
    fun `repeated full sync requests pull once`() = test {
        val coordinator = startIdle(owner = alice)

        repeat(5) { coordinator.requestFullSync() }
        advanceUntilIdle()

        assertEquals(1, pulls())
    }

    @Test
    fun `a full sync asked for during a pull gets its own drain, and no drain pulls twice`() = test {
        val coordinator = startIdle(owner = alice)
        var asked = false
        backend.beforeFetch = { table ->
            if (table == "gifts" && !asked) {
                asked = true
                coordinator.requestFullSync()
            }
        }

        coordinator.requestFullSync()
        advanceUntilIdle()

        assertEquals(listOf("Run 1", "Pull", "Run 1", "Pull"), drainSteps())
        assertEquals(2, pulls())
    }

    @Test
    fun `an unexpected failure of the pull is not retried by itself and leaves the consumer working`() = test {
        val coordinator = startIdle(owner = alice)
        var attempts = 0
        backend.beforeFetch = { table ->
            if (table == "gifts" && attempts++ == 0) throw IllegalStateException("broken invariant")
        }

        coordinator.requestFullSync()
        advanceUntilIdle()
        assertTrue(logs().contains("Sync drain failed: IllegalStateException"))

        coordinator.requestSync()
        advanceUntilIdle()
        assertEquals("a push request does not pull again", 1, attempts)

        val bela = remotePerson("Béla")
        coordinator.requestFullSync()
        advanceUntilIdle()
        assertEquals(2, attempts)
        assertNotNull(database.personDao().findById(bela, alice))
    }

    @Test
    fun `a pull refused for its session stays wanted until a new token is there`() = test {
        val coordinator = startIdle(owner = alice, secret = "t1")
        val bela = remotePerson("Béla")
        var refused = false
        backend.beforeFetch = { table ->
            if (table == "gifts" && !refused) {
                refused = true
                throw RemoteException(RemoteFailure.UNAUTHENTICATED)
            }
        }

        coordinator.requestFullSync()
        advanceUntilIdle()
        assertTrue(logs().contains("Pull failed: UNAUTHENTICATED"))
        assertNull("not retried by itself", database.personDao().findById(bela, alice))

        importSession(alice, "t2")
        advanceUntilIdle()

        assertNotNull(database.personDao().findById(bela, alice))
        assertEquals(token(alice, "t2"), backend.fetchTokens.last())
    }

    @Test
    fun `work committed before the process died is pushed, and the backend pulled, once the account is ready again`() = test {
        val anna = addPerson("Anna", alice)
        val bela = remotePerson("Béla")

        start(owner = null)
        assertEquals(0, pulls())
        activeOwner.ownerId.value = alice
        advanceUntilIdle()

        assertEquals(listOf("Run 1", "Pull"), drainSteps())
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
        assertNotNull(database.personDao().findById(bela, alice))
    }

    // ---- the full sync request belongs to the drain that takes it ----

    @Test
    fun `an account opening right after the old account's token was read for its pull gets its own pull`() = test {
        // Owner changes are not reported, so the only request for Bob's full sync is the one below.
        val coordinator = ForegroundSyncCoordinator(WatchedOwner(activeOwner.ownerId), RemoteAccounts(tokenAuth), runner, puller, scheduler, photoSync, appScope)
        importSession(alice)
        activeOwner.ownerId.value = alice
        coordinator.start()
        advanceUntilIdle()
        val cecil = remotePersonOf(bob, "Cecil")
        var reads = 0
        tokenRead = { session ->
            reads++
            if (reads == 2) {
                // Alice's pull has just read her token. Bob's account opens and asks for its full sync.
                sessionOverride = session(bob)
                activeOwner.ownerId.value = bob
                coordinator.requestFullSync()
            }
            session
        }

        coordinator.requestFullSync()
        advanceUntilIdle()

        assertTrue("Alice's pull saw the switch", logs().contains("Pull: owner changed"))
        assertNotNull("Bob's full sync was not taken by Alice's drain", database.personDao().findById(cecil, bob))
        assertEquals(token(bob), backend.fetchTokens.last())
    }

    @Test
    fun `a full sync taken by a drain that then finds no usable token is kept for the next request`() = test {
        val coordinator = startIdle(owner = alice, secret = "t1")
        val bela = remotePerson("Béla")
        var reads = 0
        // The push run's token read succeeds; the pull's finds no session.
        tokenRead = { session -> if (++reads == 2) null else session }

        coordinator.requestFullSync()
        advanceUntilIdle()
        assertTrue(logs().contains("No account to sync: UNAUTHENTICATED"))
        assertEquals(0, pulls())

        tokenRead = { it }
        importSession(alice, "t2")
        advanceUntilIdle()

        assertEquals(1, pulls())
        assertNotNull(database.personDao().findById(bela, alice))
        assertEquals(token(alice, "t2"), backend.fetchTokens.last())
    }

    @Test
    fun `an account opening between the owner check and taking the full sync request gets it back`() = test {
        val owner = WatchedOwner(activeOwner.ownerId)
        val coordinator = ForegroundSyncCoordinator(owner, RemoteAccounts(tokenAuth), runner, puller, scheduler, photoSync, appScope)
        importSession(alice)
        activeOwner.ownerId.value = alice
        coordinator.start()
        advanceUntilIdle()
        val cecil = remotePersonOf(bob, "Cecil")
        // Reads in the drain: at its start, before the push run, then before the pull.
        owner.onRead = { read ->
            if (read == 3) {
                // Bob's account opens, its token not yet in use, and asks for its full sync.
                activeOwner.ownerId.value = bob
                coordinator.requestFullSync()
            }
        }
        owner.reads = 0
        backend.fetchTokens.clear()

        coordinator.requestFullSync()
        advanceUntilIdle()
        owner.onRead = {}
        assertEquals("Alice's drain did not pull", 0, backend.fetchTokens.count { it == token(alice) })

        // Once Bob's token is in use, any request finds his full sync still wanted.
        sessionOverride = session(bob)
        coordinator.requestSync()
        advanceUntilIdle()

        assertNotNull(database.personDao().findById(cecil, bob))
    }

    // ---- conflicts ----

    @Test
    fun `a conflict settled by a push-only drain is pulled by that same drain`() = test {
        val coordinator = startIdle(owner = alice)
        val anna = conflictedPerson()

        coordinator.requestSync()
        advanceUntilIdle()

        assertEquals(listOf("Run 1", "Pull"), drainSteps())
        assertEquals("Anna (other device)", database.personDao().findById(anna, alice)!!.name)
        assertEquals(1, database.syncConflictDao().observeUnseen(alice).first().size)
    }

    @Test
    fun `a conflict settled before the push stops is pulled by a later drain, not by this one`() = test {
        val coordinator = startIdle(owner = alice)
        val anna = conflictedPerson()
        addPerson("Béla", alice)
        backend.script += listOf(Behavior.Normal, Behavior.Fail(RemoteFailure.FORBIDDEN))

        coordinator.requestSync()
        advanceUntilIdle()

        assertTrue(runs().single(), "stopped PERMISSION" in runs().single())
        assertEquals(0, pulls())
        assertEquals("Anna K.", database.personDao().findById(anna, alice)!!.name)

        coordinator.requestSync()
        advanceUntilIdle()

        assertEquals(listOf("Run 1", "Run 1", "Pull"), drainSteps())
        assertEquals("Anna (other device)", database.personDao().findById(anna, alice)!!.name)
    }

    @Test
    fun `a conflict settled just before an account switch is pulled when its account is back, never into another`() = test {
        val coordinator = startIdle(owner = alice)
        val anna = conflictedPerson()
        addPerson("Béla", alice)
        backend.script += Behavior.Normal
        val hold = hold()
        coordinator.requestSync()
        advanceUntilIdle()
        assertTrue(hold.arrived.isCompleted)

        activeOwner.ownerId.value = null
        importSession(bob)
        activeOwner.ownerId.value = bob
        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(token(bob)), backend.fetchTokens.distinct())
        assertEquals("Anna K.", database.personDao().findById(anna, alice)!!.name)
        assertEquals(1, database.syncConflictDao().observeUnseen(alice).first().size)

        activeOwner.ownerId.value = null
        importSession(alice)
        activeOwner.ownerId.value = alice
        advanceUntilIdle()

        assertEquals(token(alice), backend.fetchTokens.last())
        assertEquals("Anna (other device)", database.personDao().findById(anna, alice)!!.name)
    }

    // ---- background work ----

    @Test
    fun `a saved change asks for a background catch-up after its commit and before any drain, a new token does not`() = test {
        val coordinator = startIdle(owner = alice)
        scheduler.calls.clear()
        val writer = PersonRepositoryImpl(
            database, database.personDao(), database.occasionDao(), SyncOperationQueue(database.syncOperationDao(), clock),
            activeOwner, clock, coordinator,
            database.photoTransferDao(), GiftPhotoFiles(ApplicationProvider.getApplicationContext()),
        )
        var queuedAtCatchUp = -1
        scheduler.onCatchUp = {
            queuedAtCatchUp = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_operation").use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertTrue("no drain before the catch-up is asked for", runs().isEmpty())
        }

        val anna = writer.addPerson("Anna", null)
        assertEquals("asked for after the commit", 1, queuedAtCatchUp)
        advanceUntilIdle()

        assertEquals(listOf(FakeSyncScheduler.CATCH_UP), scheduler.calls)
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))

        importSession(alice, "refreshed")
        advanceUntilIdle()
        assertEquals("a token only wakes", listOf(FakeSyncScheduler.CATCH_UP), scheduler.calls)
        assertEquals(2, runs().size)
    }

    @Test
    fun `a failure to schedule never fails the save, and the change is still pushed`() = test {
        val coordinator = startIdle(owner = alice)
        scheduler.onCatchUp = { throw IllegalStateException("WorkManager is not initialized") }
        val writer = PersonRepositoryImpl(
            database, database.personDao(), database.occasionDao(), SyncOperationQueue(database.syncOperationDao(), clock),
            activeOwner, clock, coordinator,
            database.photoTransferDao(), GiftPhotoFiles(ApplicationProvider.getApplicationContext()),
        )

        val anna = writer.addPerson("Anna", null)
        advanceUntilIdle()

        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
        assertTrue(logs().contains("Could not schedule background sync: IllegalStateException"))
    }

    @Test
    fun `an account becoming ready keeps the periodic job scheduled`() = test {
        start(owner = alice)

        assertEquals(listOf(FakeSyncScheduler.PERIODIC), scheduler.calls)
    }

    @Test
    fun `a drain already under way never answers a later wait, the next one does`() = test {
        val coordinator = startIdle(owner = alice)
        val anna = addPerson("Anna", alice)
        // The drain under way gets no answer, so it would end transient; the next one delivers.
        val hold = Behavior.Hold(then = Behavior.LoseRequest).also { backend.script += listOf(it, Behavior.LoseRequest) }
        coordinator.requestSync()
        advanceUntilIdle()
        assertTrue(hold.arrived.isCompleted)

        val waiter = async(start = CoroutineStart.UNDISPATCHED) { coordinator.syncAndAwait(full = false) }
        advanceUntilIdle()
        assertFalse(waiter.isCompleted)

        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(DrainOutcome.DONE, waiter.await())
        assertEquals(2, runs().size)
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
    }

    @Test
    fun `waits made before a drain starts share it, also one racing a pending wake-up`() = test {
        val coordinator = startIdle(owner = alice)
        addPerson("Anna", alice)
        // Only the drain that takes the waits pulls, as one of them asks, and its pull fails; the
        // drain their own wake-ups add afterwards pulls nothing and finishes cleanly.
        backend.beforeFetch = { throw RemoteException(RemoteFailure.NETWORK) }
        // A wake-up already handed to the consumer, and two waits registered before it drains.
        coordinator.requestSync()
        val first = async(start = CoroutineStart.UNDISPATCHED) { coordinator.syncAndAwait(full = false) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { coordinator.syncAndAwait(full = true) }
        advanceUntilIdle()

        assertEquals("answered by the first drain", DrainOutcome.TRANSIENT, first.await())
        assertEquals(DrainOutcome.TRANSIENT, second.await())
        assertEquals(listOf("Run 1", "Pull", "Run 1"), drainSteps())
    }

    @Test
    fun `giving up a wait leaves the drain and every other wait alone`() = test {
        val coordinator = startIdle(owner = alice)
        val anna = addPerson("Anna", alice)
        val hold = hold()
        val abandoned = async(start = CoroutineStart.UNDISPATCHED) { coordinator.syncAndAwait(full = false) }
        val kept = async(start = CoroutineStart.UNDISPATCHED) { coordinator.syncAndAwait(full = false) }
        advanceUntilIdle()
        assertTrue(hold.arrived.isCompleted)

        abandoned.cancel()
        hold.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(DrainOutcome.DONE, kept.await())
        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
    }

    @Test
    fun `a drain that fails unexpectedly answers its waits as transient`() = test {
        val coordinator = startIdle(owner = alice)
        addPerson("Anna", alice)
        backend.script += Behavior.Crash(IllegalStateException("broken invariant"))

        assertEquals(DrainOutcome.TRANSIENT, coordinator.syncAndAwait(full = false))
    }

    @Test
    fun `a drain's outcome comes only from what it saw`() = test {
        val coordinator = startIdle(owner = alice)
        suspend fun outcome(full: Boolean = false) = coordinator.syncAndAwait(full).also { advanceUntilIdle() }

        assertEquals("nothing to do", DrainOutcome.DONE, outcome(full = true))

        addPerson("Lost", alice)
        backend.script += listOf(Behavior.LoseRequest, Behavior.LoseRequest)
        assertEquals("no answer", DrainOutcome.TRANSIENT, outcome())
        assertEquals("then delivered", DrainOutcome.DONE, outcome())

        backend.beforeFetch = { throw RemoteException(RemoteFailure.NETWORK) }
        assertEquals("the pull failed", DrainOutcome.TRANSIENT, outcome(full = true))
        backend.beforeFetch = {}

        addPerson("Refused", alice)
        backend.script += Behavior.Fail(RemoteFailure.FORBIDDEN)
        assertEquals(DrainOutcome.NOT_PERMITTED, outcome())

        tokenRead = { null }
        assertEquals(DrainOutcome.SESSION_UNUSABLE, outcome())
        tokenRead = { it }

        activeOwner.ownerId.value = null
        assertEquals(DrainOutcome.NO_OWNER, outcome())
    }

    @Test
    fun `a gift's photo is uploaded before its row, offline it makes the drain transient, and a replaced one goes last`() = test {
        val coordinator = startIdle(owner = alice)
        val files = GiftPhotoFiles(ApplicationProvider.getApplicationContext())
        val gifts = GiftRepositoryImpl(
            database, database.giftDao(), database.personDao(), database.occasionDao(), SyncOperationQueue(database.syncOperationDao(), clock),
            activeOwner, clock, SyncRequester {}, database.photoTransferDao(), files,
        )
        val anna = addPerson("Anna", alice)
        fun newPhoto() = files.newPath(alice).also { files.write(it, byteArrayOf(1, 2, 3)) }
        fun details(photoPath: String) =
            GiftDetails(GiftDirection.GIVEN, null, anna.id, null, "Book", LocalDate.of(2026, 5, 12), null, null, null, photoPath)
        fun remotePhotoOf(id: UUID) = (backend.rows[id]?.fields as RemoteGiftFields?)?.photoPath
        val old = newPhoto()
        val gift = gifts.addGift(details(old))

        photoRemote.failure = RemoteFailure.NETWORK
        assertEquals(DrainOutcome.TRANSIENT, coordinator.syncAndAwait(full = false))
        assertNotNull("the person still went", backend.rows[anna.id])
        assertNull(backend.rows[gift.id])

        photoRemote.failure = null
        assertEquals(DrainOutcome.DONE, coordinator.syncAndAwait(full = false))
        assertEquals(old, remotePhotoOf(gift.id))

        val new = newPhoto()
        gifts.updateGift(gift.id, details(new))
        assertEquals(DrainOutcome.DONE, coordinator.syncAndAwait(full = false))
        assertEquals(new, remotePhotoOf(gift.id))
        assertEquals(listOf("upload $old", "upload $old", "upload $new", "delete $old"), photoRemote.calls.toList())
        assertEquals(setOf(new), photoRemote.objects.keys.toSet())
    }

    @Test
    fun `a drain that ran out of pushes leaves the rest deferred, not failed`() = test {
        val coordinator = startIdle(owner = alice)
        repeat(2 * SyncRunner.MAX_PUSHES + 1) { addPerson("Person $it", alice) }

        assertEquals(DrainOutcome.DEFERRED, coordinator.syncAndAwait(full = false))
        assertEquals(DrainOutcome.DONE, coordinator.syncAndAwait(full = false))
        assertTrue(operations.getAllForOwner(alice).isEmpty())
    }

    @Test
    fun `a background job in a later process pushes a change whose catch-up was never scheduled`() = test {
        // Committed, but the process died before asking for a catch-up.
        val anna = addPerson("Anna", alice)
        val bela = remotePerson("Béla")
        // A token to capture that no token collector hears of, and an account whose opening reaches
        // no collector either: in this new process only the job asks for a drain.
        sessionOverride = session(alice)
        activeOwner.ownerId.value = alice
        val coordinator = ForegroundSyncCoordinator(UnreportedOwner(alice), RemoteAccounts(tokenAuth), runner, puller, scheduler, photoSync, appScope)
        coordinator.start()
        advanceUntilIdle()
        assertTrue(runs().isEmpty())
        val ready = MutableStateFlow<OwnerSessionState>(OwnerSessionState.Ready(AuthUser(alice, "alice@example.com")))
        val job = BackgroundSync(ready, { BackgroundSessionResult.READY }, coordinator::syncAndAwait)

        assertFalse("done, not retried", job.run(full = true, attempt = 0))

        assertEquals(RemoteState.SYNCED, remoteStateOf(anna))
        assertNotNull(database.personDao().findById(bela, alice))
        assertEquals(listOf("Run 1", "Pull"), drainSteps())
    }

    /**
     * An active owner whose every read of the current value can be watched. Changes are never
     * reported to collectors, so a test makes every request itself.
     */
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private class WatchedOwner(private val owner: StateFlow<UUID?>) : ActiveOwner {
        var reads = 0
        var onRead: (Int) -> Unit = {}
        override val ownerId: StateFlow<UUID?> = object : StateFlow<UUID?> by owner {
            override val value: UUID?
                get() = owner.value.also { onRead(++reads) }

            override suspend fun collect(collector: FlowCollector<UUID?>): Nothing = awaitCancellation()
        }
    }

    // ---- fixtures ----

    /** An account that is open, while collectors of [ownerId] never hear of it. */
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private class UnreportedOwner(owner: UUID) : ActiveOwner {
        override val ownerId: StateFlow<UUID?> = object : StateFlow<UUID?> {
            override val value: UUID? = owner
            override val replayCache: List<UUID?> = listOf(owner)
            override suspend fun collect(collector: FlowCollector<UUID?>): Nothing = awaitCancellation()
        }
    }

    private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            appScope.cancel()
        }
    }

    private fun newCoordinator(scope: CoroutineScope) =
        ForegroundSyncCoordinator(activeOwner, RemoteAccounts(tokenAuth), runner, puller, scheduler, photoSync, scope)

    // Every token the coordinator captures is read through here: a test may replace the session,
    // or act at the exact moment a token is read.
    private var sessionOverride: UserSession? = null
    private var tokenRead: (UserSession?) -> UserSession? = { it }
    private val tokenAuth: Auth = object : Auth by auth {
        override fun currentSessionOrNull(): UserSession? = tokenRead(sessionOverride ?: auth.currentSessionOrNull())
    }

    /** A person only the backend has, of [owner]. */
    private fun remotePersonOf(owner: UUID, name: String): UUID = UUID.randomUUID().also {
        backend.seed(it, owner, 1, UUID.randomUUID(), RemotePersonFields(name, null, null, null, null))
    }

    /** Starts a coordinator for [owner], signed in with a token made with [secret], and lets its first drain finish. */
    private suspend fun TestScope.start(owner: UUID?, secret: String? = "signature-${owner ?: alice}"): ForegroundSyncCoordinator {
        // Without an owner, e.g. while restoring, Alice's stored session may already be there.
        if (secret != null) importSession(owner ?: alice, secret)
        activeOwner.ownerId.value = owner
        return newCoordinator(appScope).also {
            it.start()
            advanceUntilIdle()
        }
    }

    /** Like [start], then forgets the runs so far, for tests about what later requests do. */
    private suspend fun TestScope.startIdle(owner: UUID, secret: String = "signature-$owner"): ForegroundSyncCoordinator =
        start(owner, secret).also {
            ShadowLog.reset()
            backend.fetches.clear()
            backend.fetchTokens.clear()
        }

    /** The push did not get through, yet the pull still runs, once, around the queued change. */
    private suspend fun TestScope.assertUnfinishedPushStillPulls(failure: List<Behavior>) {
        val anna = addPerson("Anna", alice)
        backend.script += failure
        val bela = remotePerson("Béla")

        start(owner = alice)

        assertEquals("Run 1: pushed 1, settled 0", runs().single().substringBefore(", stopped"))
        assertEquals(1, pulls())
        assertNotNull("unrelated rows still arrive", database.personDao().findById(bela, alice))
        assertNotNull("the change is still queued", operations.findForEntity(SyncEntityType.PERSON, anna.id))
        assertEquals(RemoteState.LOCAL_ONLY, remoteStateOf(anna))
    }

    /** Full reads of the backend so far: each pull reads the gifts first. */
    private fun pulls(): Int = backend.fetches.count { it == "gifts" }

    /** What each drain did, in order: "Run n" for a push run, "Pull" for a pull. */
    private fun drainSteps(): List<String> =
        logs().filter { it.startsWith("Run ") || it.startsWith("Pull") }.map { if (it.startsWith("Pull")) "Pull" else it.substringBefore(":") }

    /** A person only the backend has, as if another device created it. */
    private fun remotePerson(name: String): UUID = UUID.randomUUID().also {
        backend.seed(it, alice, 1, UUID.randomUUID(), RemotePersonFields(name, null, null, null, null))
    }

    private suspend fun importSession(owner: UUID, secret: String = "signature-$owner") {
        auth.importSession(session(owner, secret), autoRefresh = false, source = SessionSource.Storage)
    }

    private fun session(owner: UUID, secret: String = "signature-$owner") = UserSession(
        accessToken = token(owner, secret),
        refreshToken = "refresh-$secret",
        expiresIn = 3600,
        tokenType = "bearer",
        user = UserInfo(aud = "authenticated", id = owner.toString()),
    )

    /** The next write waits until the test releases it. */
    private fun hold() = Behavior.Hold().also { backend.script += it }

    private fun logs(): List<String> = ShadowLog.getLogsForTag("ForegroundSync").map { it.msg }

    private fun runs(): List<String> = logs().filter { it.startsWith("Run ") }

    /** Adds a person as [owner], with a later createdAt than the one before, so push order is known. */
    private suspend fun addPerson(name: String, owner: UUID): Person {
        val active = activeOwner.ownerId.value
        activeOwner.ownerId.value = owner
        return people.addPerson(name, null).also {
            activeOwner.ownerId.value = active
            clock.advanceBy(Duration.ofSeconds(1))
        }
    }

    private suspend fun pending(person: Person): SyncOperationEntity = pending(person.id)

    private suspend fun pending(id: UUID): SyncOperationEntity =
        checkNotNull(operations.findForEntity(SyncEntityType.PERSON, id)) { "Nothing pending" }

    private suspend fun remoteStateOf(person: Person): RemoteState =
        database.personDao().findById(person.id, alice)!!.remoteState

    /** A person the backend already has at [version], with nothing pending locally. */
    private suspend fun syncedPerson(name: String, version: Long): UUID {
        val person = addPerson(name, alice)
        val dao = database.personDao()
        val entity = dao.findById(person.id, alice)!!.copy(version = version, remoteState = RemoteState.SYNCED)
        dao.update(entity)
        operations.delete(pending(person))
        backend.seed(person.id, alice, version, UUID.randomUUID(), entity.toRemoteFields())
        return person.id
    }

    /**
     * Alice's person at version 3, edited here while another device moved the backend's row to
     * version 4: pushing the edit settles a conflict.
     */
    private suspend fun conflictedPerson(): UUID {
        val anna = syncedPerson("Anna", version = 3)
        val active = activeOwner.ownerId.value
        activeOwner.ownerId.value = alice
        people.updatePerson(anna, "Anna K.", null)
        activeOwner.ownerId.value = active
        clock.advanceBy(Duration.ofSeconds(1))
        backend.rows.getValue(anna).apply {
            version = 4
            lastMutationId = UUID.randomUUID()
            fields = (fields as RemotePersonFields).copy(name = "Anna (other device)")
        }
        return anna
    }

    private fun newPusher() = SyncPusher(
        database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
        database.giftDao(), database.wishlistDao(), database.wishDao(),
        SyncReconciler(
            database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
            database.giftDao(), database.wishlistDao(), database.wishDao(), database.syncConflictDao(), clock,
        ),
        backend, backend, clock,
    )
}
