package app.gifty.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.SyncReconciler
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.RemoteAccounts
import app.gifty.data.remote.RemoteFailure
import app.gifty.data.remote.RemoteTestData.token
import app.gifty.data.remote.toRemoteFields
import app.gifty.data.repository.PersonRepositoryImpl
import app.gifty.data.sync.FakeBackend.Behavior
import app.gifty.domain.model.Person
import app.gifty.testing.FakeActiveOwner
import app.gifty.testing.MutableClock
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
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
import java.util.UUID

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
    private lateinit var database: GiftyDatabase
    private lateinit var backend: FakeBackend
    private lateinit var runner: SyncRunner
    private lateinit var people: PersonRepositoryImpl

    private val operations get() = database.syncOperationDao()

    @Before
    fun setUp() {
        ShadowLog.reset()
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftyDatabase::class.java)
            // Room work runs on the test scheduler, which runs on the test's (main) thread.
            .setQueryCoroutineContext(dispatcher)
            .allowMainThreadQueries()
            .build()
        // Writes made here never ask for sync, like a process that died right after committing.
        people = PersonRepositoryImpl(
            database, database.personDao(), database.occasionDao(), SyncOperationQueue(database.syncOperationDao(), clock),
            activeOwner, clock, SyncRequester {},
        )
        backend = FakeBackend(database)
        runner = SyncRunner(
            database.syncOperationDao(),
            SyncDependencies(database.personDao(), database.occasionDao(), database.giftIdeaDao(), database.giftDao()),
            newPusher(),
            activeOwner,
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

    // ---- fixtures ----

    private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            appScope.cancel()
        }
    }

    private fun newCoordinator(scope: CoroutineScope) =
        ForegroundSyncCoordinator(activeOwner, RemoteAccounts(auth), runner, scope)

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
        start(owner, secret).also { ShadowLog.reset() }

    private suspend fun importSession(owner: UUID, secret: String = "signature-$owner") {
        val session = UserSession(
            accessToken = token(owner, secret),
            refreshToken = "refresh-$secret",
            expiresIn = 3600,
            tokenType = "bearer",
            user = UserInfo(aud = "authenticated", id = owner.toString()),
        )
        auth.importSession(session, autoRefresh = false, source = SessionSource.Storage)
    }

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

    private fun newPusher() = SyncPusher(
        database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
        database.giftDao(),
        SyncReconciler(
            database, database.syncOperationDao(), database.personDao(), database.occasionDao(), database.giftIdeaDao(),
            database.giftDao(),
        ),
        backend, backend, clock,
    )
}
