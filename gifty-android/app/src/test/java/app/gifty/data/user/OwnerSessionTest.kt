package app.gifty.data.user

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.LegacyOwnershipClaim
import app.gifty.data.local.SyncOperationQueue
import app.gifty.data.local.dao.LegacyOwnershipDao
import app.gifty.data.local.entity.PersonEntity
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.reminders.DeviceNotificationAccess
import app.gifty.data.reminders.ReminderNotifier
import app.gifty.data.reminders.ReminderPreferences
import app.gifty.data.reminders.ReminderSignOut
import app.gifty.data.repository.PersonRepositoryImpl
import app.gifty.data.sync.SyncRequester
import app.gifty.domain.model.AuthState
import app.gifty.domain.model.AuthUser
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.UpcomingOccasion
import app.gifty.domain.repository.NoActiveOwnerException
import app.gifty.domain.repository.OccasionDetails
import app.gifty.domain.repository.OwnerSessionState
import app.gifty.testing.FakeAuthRepository
import app.gifty.testing.FakeReminderScheduler
import app.gifty.testing.FakeSyncScheduler
import app.gifty.testing.occasion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OwnerSessionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val legacy = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val alice = AuthUser(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), "alice@example.com")
    private val bob = AuthUser(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"), "bob@example.com")

    private val auth = FakeAuthRepository()
    private val scheduler = FakeSyncScheduler()
    private val reminders = FakeReminderScheduler()
    private val notifier = ReminderNotifier(context, DeviceNotificationAccess(context))
    private val reminderPreferences = ReminderPreferences(context)
    private lateinit var database: GiftyDatabase
    private lateinit var claimDao: ControlledClaimDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, GiftyDatabase::class.java).build()
        claimDao = ControlledClaimDao(database.legacyOwnershipDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Starts a session whose every owner and state change is recorded, in order. */
    private fun TestScope.startSession(): Recorded {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val session = OwnerSession(auth, LegacyOwnerStore(context), LegacyOwnershipClaim(database, claimDao), scheduler, ReminderSignOut(reminders, notifier), scope)
        val recorded = Recorded(session)
        scope.launch { session.ownerId.collect { recorded.owners += it } }
        scope.launch { session.state.collect { recorded.states += it } }
        session.start()
        return recorded
    }

    private class Recorded(val session: OwnerSession) {
        val owners = mutableListOf<UUID?>()
        val states = mutableListOf<OwnerSessionState>()
    }

    // The claim runs on Room's and the IO dispatcher's real threads, so waits use real time.
    private suspend fun <T> inRealTime(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(5_000) { block() } }

    private suspend fun OwnerSession.awaitState(expected: OwnerSessionState) = inRealTime { state.first { it == expected } }

    private suspend fun ControlledClaimDao.awaitClaimStarted(account: UUID) = inRealTime { started.first { account in it } }

    @Test
    fun `without a saved session nothing is open`() = runTest {
        auth.state.value = AuthState.SignedOut

        val recorded = startSession()

        assertEquals(OwnerSessionState.SignedOut, recorded.session.state.value)
        assertNull(recorded.session.ownerId.value)
        assertEquals(listOf<UUID?>(null), recorded.owners)
    }

    @Test
    fun `an account without legacy data opens without a claim`() = runTest {
        auth.state.value = AuthState.SignedIn(alice)

        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))

        assertEquals(alice.id, recorded.session.ownerId.value)
        assertEquals(0, claimDao.calls)
    }

    @Test
    fun `legacy data is claimed before the account opens`() = runTest {
        val anna = seedLegacyData()
        auth.state.value = AuthState.SignedIn(alice)

        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))

        assertEquals(alice.id, database.personDao().findActiveById(anna, alice.id)?.ownerUserId)
        assertEquals(listOf(SyncOperation.CREATE), database.syncOperationDao().getPending(alice.id, 10).map { it.operation })
        // The owner went straight from nothing to the account: never the legacy id.
        assertEquals(listOf(null, alice.id), recorded.owners)
    }

    @Test
    fun `nothing is open while the claim is still running`() = runTest {
        seedLegacyData()
        val gate = claimDao.holdClaimFor(alice.id)
        auth.state.value = AuthState.SignedIn(alice)

        val recorded = startSession()
        claimDao.awaitClaimStarted(alice.id)

        assertEquals(OwnerSessionState.Restoring, recorded.session.state.value)
        assertNull(recorded.session.ownerId.value)

        gate.complete(Unit)
        recorded.session.awaitState(OwnerSessionState.Ready(alice))
        assertEquals(alice.id, recorded.session.ownerId.value)
    }

    @Test
    fun `a failed claim leaves nothing open until it is retried`() = runTest {
        val anna = seedLegacyData()
        claimDao.failuresLeft = 1
        auth.state.value = AuthState.SignedIn(alice)

        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Failed(alice))
        assertNull(recorded.session.ownerId.value)
        assertEquals(legacy, ownerOfPerson(anna))

        recorded.session.retry()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))

        assertEquals(alice.id, recorded.session.ownerId.value)
        assertEquals(alice.id, ownerOfPerson(anna))
    }

    @Test
    fun `retry does nothing unless preparing the account failed`() = runTest {
        auth.state.value = AuthState.SignedIn(alice)
        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))
        val statesBefore = recorded.states.toList()

        recorded.session.retry()

        assertEquals(statesBefore, recorded.states)
    }

    @Test
    fun `the same account reported again stays open without closing or claiming`() = runTest {
        seedLegacyData()
        auth.state.value = AuthState.SignedIn(alice)
        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))
        val claimsSoFar = claimDao.calls
        val ownersSoFar = recorded.owners.toList()
        val statesSoFar = recorded.states.toList()

        // A different value for the same account, so the auth flow really emits again.
        auth.state.value = AuthState.SignedIn(alice.copy(email = "alice@example.org"))

        assertEquals(ownersSoFar, recorded.owners)
        assertEquals(statesSoFar, recorded.states)
        assertEquals(claimsSoFar, claimDao.calls)
    }

    @Test
    fun `an account still being prepared is never opened once another account signed in`() = runTest {
        val anna = seedLegacyData()
        claimDao.holdClaimFor(alice.id)
        auth.state.value = AuthState.SignedIn(alice)
        val recorded = startSession()
        claimDao.awaitClaimStarted(alice.id)

        auth.state.value = AuthState.SignedIn(bob)
        recorded.session.awaitState(OwnerSessionState.Ready(bob))

        assertFalse(alice.id in recorded.owners)
        assertFalse(recorded.states.any { it == OwnerSessionState.Ready(alice) })
        // Alice's interrupted claim rolled back, so the legacy data went to Bob.
        assertEquals(bob.id, ownerOfPerson(anna))
    }

    @Test
    fun `switching accounts closes the first account before opening the second`() = runTest {
        auth.state.value = AuthState.SignedIn(alice)
        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))

        auth.state.value = AuthState.SignedIn(bob)
        recorded.session.awaitState(OwnerSessionState.Ready(bob))

        assertEquals(listOf(null, alice.id, null, bob.id), recorded.owners)
    }

    @Test
    fun `only an explicit sign-out cancels background sync and reminders, not restoring or another account`() = runTest {
        auth.state.value = AuthState.SignedIn(alice)
        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))
        auth.state.value = AuthState.Restoring
        auth.state.value = AuthState.SignedIn(bob)
        recorded.session.awaitState(OwnerSessionState.Ready(bob))
        assertTrue(scheduler.calls.isEmpty())
        assertTrue(reminders.calls.isEmpty())

        recorded.session.signOut()

        assertEquals(listOf(FakeSyncScheduler.CANCEL), scheduler.calls)
        assertEquals(listOf(FakeReminderScheduler.CANCEL), reminders.calls)
    }

    @Test
    fun `signing out closes the data before the network sign-out finishes`() = runTest {
        auth.state.value = AuthState.SignedIn(alice)
        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        reminderPreferences.setEnabled(alice.id, true)
        reminderPreferences.setLastDigest(alice.id, LocalDate.of(2026, 5, 10))
        notifier.post(alice.id, listOf(digestEntry()))
        val gate = CompletableDeferred<Unit>()
        auth.signOutGate = gate

        recorded.session.signOut()

        assertEquals(1, auth.signOutCalls)
        assertNull(recorded.session.ownerId.value)
        assertEquals(OwnerSessionState.Restoring, recorded.session.state.value)
        // Reminders stop at once too: no digest left, no heartbeat; the account's choice and day stay.
        assertTrue(activeDigests().isEmpty())
        assertEquals(listOf(FakeReminderScheduler.CANCEL), reminders.calls)
        assertTrue(reminderPreferences.isEnabled(alice.id))
        assertEquals(LocalDate.of(2026, 5, 10), reminderPreferences.lastDigest(alice.id))

        gate.complete(Unit)
        recorded.session.awaitState(OwnerSessionState.SignedOut)
        assertNull(recorded.session.ownerId.value)
    }

    @Test
    fun `a sign-out that leaves the session in place reopens the same account`() = runTest {
        auth.state.value = AuthState.SignedIn(alice)
        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))
        auth.signOutFailure = IllegalStateException("simulated")

        recorded.session.signOut()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))

        assertEquals(listOf(null, alice.id, null, alice.id), recorded.owners)
    }

    @Test
    fun `restored data without the legacy id is left alone, not guessed`() = runTest {
        // A restored database whose legacy preference is missing: rows and an unowned operation.
        val anna = seedLegacyData()
        context.getSharedPreferences("local_user", Context.MODE_PRIVATE).edit().clear().commit()
        auth.state.value = AuthState.SignedIn(alice)

        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))

        assertEquals(0, claimDao.calls)
        assertEquals(legacy, ownerOfPerson(anna))
        assertEquals(1, count("SELECT COUNT(*) FROM sync_operation WHERE ownerUserId IS NULL"))
        assertFalse(context.getSharedPreferences("local_user", Context.MODE_PRIVATE).contains("user_id"))
    }

    @Test
    fun `the session and the repositories keep accounts apart end to end`() = runTest {
        val anna = seedLegacyData()
        auth.state.value = AuthState.SignedIn(alice)
        val recorded = startSession()
        val session = recorded.session
        val people = PersonRepositoryImpl(
            database, database.personDao(), database.occasionDao(),
            SyncOperationQueue(database.syncOperationDao(), Clock.systemUTC()), session, Clock.systemUTC(),
            SyncRequester {},
        )
        session.awaitState(OwnerSessionState.Ready(alice))

        val alicesPerson = people.addPerson("Béla", null)
        assertEquals(setOf(anna, alicesPerson.id), people.observePeople().first().map { it.id }.toSet())

        auth.state.value = AuthState.SignedIn(bob)
        session.awaitState(OwnerSessionState.Ready(bob))
        assertEquals(emptyList<UUID>(), people.observePeople().first().map { it.id })
        val bobsPerson = people.addPerson("Csilla", null)

        val queue = database.syncOperationDao()
        assertEquals(setOf(anna, alicesPerson.id), queue.getPending(alice.id, 10).map { it.entityId }.toSet())
        assertEquals(listOf(bobsPerson.id), queue.getPending(bob.id, 10).map { it.entityId })
        assertEquals(0, count("SELECT COUNT(*) FROM sync_operation WHERE ownerUserId IS NULL"))

        session.signOut()
        assertEquals(emptyList<UUID>(), people.observePeople().first().map { it.id })
        assertTrue(runCatching { people.addPerson("Dóra", null) }.exceptionOrNull() is NoActiveOwnerException)
    }

    // ---- data ----

    /** Data from before accounts existed: a person and its pending create, migrated without an owner. */
    private suspend fun seedLegacyData(): UUID {
        context.getSharedPreferences("local_user", Context.MODE_PRIVATE)
            .edit().putString("user_id", legacy.toString()).commit()
        val now = Instant.parse("2026-05-01T12:00:00Z")
        val anna = PersonEntity(
            id = UUID.randomUUID(), ownerUserId = legacy, name = "Anna", relationship = null, photoPath = null,
            linkedUserId = null, version = 0, remoteState = RemoteState.LOCAL_ONLY, createdAt = now, updatedAt = now,
            deletedAt = null,
        )
        database.personDao().insert(anna)
        database.syncOperationDao().insert(
            SyncOperationEntity(
                id = UUID.randomUUID(), ownerUserId = null, entityType = SyncEntityType.PERSON, entityId = anna.id,
                operation = SyncOperation.CREATE, mutationId = UUID.randomUUID(), inFlightMutationId = null, blockedReason = null,
                createdAt = now, attemptCount = 0, lastAttemptAt = null,
                lastErrorCode = null,
            ),
        )
        return anna.id
    }

    private fun count(sql: String): Int =
        database.openHelper.readableDatabase.query(sql).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun ownerOfPerson(id: UUID): UUID? =
        database.openHelper.readableDatabase.query("SELECT ownerUserId FROM person WHERE id = ?", arrayOf(id.toString()))
            .use { cursor -> if (cursor.moveToFirst()) UUID.fromString(cursor.getString(0)) else null }

    /** The real claim statements, which can be held or made to fail for one account. */
    private class ControlledClaimDao(private val real: LegacyOwnershipDao) : LegacyOwnershipDao by real {
        var calls = 0
        var failuresLeft = 0
        val started = kotlinx.coroutines.flow.MutableStateFlow(emptySet<UUID>())
        private val gates = mutableMapOf<UUID, CompletableDeferred<Unit>>()

        fun holdClaimFor(account: UUID) = CompletableDeferred<Unit>().also { gates[account] = it }

        override suspend fun claimPeople(legacyOwnerId: UUID, accountOwnerId: UUID): Int {
            calls++
            started.value += accountOwnerId
            gates[accountOwnerId]?.await()
            if (failuresLeft > 0) {
                failuresLeft--
                throw IllegalStateException("simulated failure")
            }
            return real.claimPeople(legacyOwnerId, accountOwnerId)
        }
    }

    private fun activeDigests() = context.getSystemService(NotificationManager::class.java).activeNotifications
        .filter { it.notification.channelId == ReminderNotifier.CHANNEL_ID }

    private fun digestEntry() = UpcomingOccasion(
        occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true)),
        null,
        LocalDate.of(2026, 12, 24),
        0,
        null,
    )
}
