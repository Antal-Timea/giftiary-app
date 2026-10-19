package app.giftiary.data.user

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.local.SyncOperationQueue
import app.giftiary.data.local.entity.PersonEntity
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.entity.SyncOperationEntity
import app.giftiary.data.reminders.DeviceNotificationAccess
import app.giftiary.data.reminders.ReminderNotifier
import app.giftiary.data.reminders.ReminderPreferences
import app.giftiary.data.reminders.ReminderSignOut
import app.giftiary.data.repository.PersonRepositoryImpl
import app.giftiary.data.sync.SyncRequester
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.UpcomingOccasion
import app.giftiary.domain.repository.NoActiveOwnerException
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.testing.FakeAuthRepository
import app.giftiary.testing.FakeRecoveryGate
import app.giftiary.testing.FakeReminderScheduler
import app.giftiary.testing.FakeSyncScheduler
import app.giftiary.testing.occasion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
    private val recoveryGate = FakeRecoveryGate()
    private val scheduler = FakeSyncScheduler()
    private val reminders = FakeReminderScheduler()
    private val notifier = ReminderNotifier(context, DeviceNotificationAccess(context))
    private val reminderPreferences = ReminderPreferences(context)
    private lateinit var database: GiftiaryDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, GiftiaryDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Starts a session whose every owner and state change is recorded, in order. */
    private fun TestScope.startSession(): Recorded {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val session = OwnerSession(auth, scheduler, ReminderSignOut(reminders, notifier), recoveryGate, scope)
        val recorded = Recorded(session)
        scope.launch {
            session.ownerId.collect {
                recorded.owners += it
                recorded.openings += it to session.state.value
            }
        }
        scope.launch { session.state.collect { recorded.states += it } }
        session.start()
        return recorded
    }

    private class Recorded(val session: OwnerSession) {
        val owners = mutableListOf<UUID?>()
        val states = mutableListOf<OwnerSessionState>()

        // Each owner as it was published, with the state seen at that moment.
        val openings = mutableListOf<Pair<UUID?, OwnerSessionState>>()
    }

    private suspend fun OwnerSession.awaitState(expected: OwnerSessionState) = withTimeout(5_000) { state.first { it == expected } }

    @Test
    fun `a session that only exists for a password recovery opens nothing until the recovery is done`() = runTest {
        auth.state.value = AuthState.SignedOut
        val recorded = startSession()

        // The recovery closes the gate before it exchanges the link, which signs the account in.
        recoveryGate.isActive.value = true
        auth.state.value = AuthState.SignedIn(alice)

        assertEquals(OwnerSessionState.Restoring, recorded.session.state.value)
        assertNull(recorded.session.ownerId.value)
        assertTrue("no owner was ever opened", recorded.owners.all { it == null })
        assertFalse(recorded.states.any { it is OwnerSessionState.Ready })

        recoveryGate.isActive.value = false
        recorded.session.awaitState(OwnerSessionState.Ready(alice))
        assertEquals(alice.id, recorded.session.ownerId.value)
    }

    @Test
    fun `a recovery given up through sign-out never opens the account`() = runTest {
        auth.state.value = AuthState.SignedOut
        val recorded = startSession()
        recoveryGate.isActive.value = true
        auth.state.value = AuthState.SignedIn(alice)

        recorded.session.signOut()
        recorded.session.awaitState(OwnerSessionState.SignedOut)
        recoveryGate.isActive.value = false

        assertEquals(OwnerSessionState.SignedOut, recorded.session.state.value)
        assertTrue(recorded.owners.all { it == null })
        assertFalse(recorded.states.any { it is OwnerSessionState.Ready })
    }

    @Test
    fun `without a saved session nothing is open`() = runTest {
        auth.state.value = AuthState.SignedOut

        val recorded = startSession()

        assertEquals(OwnerSessionState.SignedOut, recorded.session.state.value)
        assertNull(recorded.session.ownerId.value)
        assertEquals(listOf<UUID?>(null), recorded.owners)
    }

    @Test
    fun `a saved session opens its account`() = runTest {
        auth.state.value = AuthState.SignedIn(alice)

        val recorded = startSession()

        assertEquals(OwnerSessionState.Ready(alice), recorded.session.state.value)
        assertEquals(listOf(null, alice.id), recorded.owners)
    }

    @Test
    fun `the same account reported again stays open without closing`() = runTest {
        auth.state.value = AuthState.SignedIn(alice)
        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))
        val ownersSoFar = recorded.owners.toList()
        val statesSoFar = recorded.states.toList()

        // A different value for the same account, so the auth flow really emits again.
        auth.state.value = AuthState.SignedIn(alice.copy(email = "alice@example.org"))

        assertEquals(ownersSoFar, recorded.owners)
        assertEquals(statesSoFar, recorded.states)
    }

    @Test
    fun `switching accounts closes the first account before opening the second`() = runTest {
        auth.state.value = AuthState.SignedIn(alice)
        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))

        auth.state.value = AuthState.SignedIn(bob)
        recorded.session.awaitState(OwnerSessionState.Ready(bob))

        assertEquals(bob.id, recorded.session.ownerId.value)
        assertFalse("Alice never again after Bob", alice.id in recorded.owners.dropWhile { it != bob.id })
        // Never one account as owner while the state still names the other.
        recorded.openings.forEach { (owner, state) ->
            assertTrue("$owner in $state", state !is OwnerSessionState.Ready || state.user.id == owner)
        }
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
    fun `data from before accounts is neither opened nor handed to the account`() = runTest {
        val anna = seedLegacyData()
        auth.state.value = AuthState.SignedIn(alice)

        val recorded = startSession()
        recorded.session.awaitState(OwnerSessionState.Ready(alice))

        assertEquals(legacy, ownerOfPerson(anna))
        assertEquals(1, count("SELECT COUNT(*) FROM sync_operation WHERE ownerUserId IS NULL"))
        assertTrue(database.syncOperationDao().getPending(alice.id, 10).isEmpty())
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
        assertEquals(listOf(alicesPerson.id), people.observePeople().first().map { it.id })

        auth.state.value = AuthState.SignedIn(bob)
        session.awaitState(OwnerSessionState.Ready(bob))
        assertEquals(emptyList<UUID>(), people.observePeople().first().map { it.id })
        val bobsPerson = people.addPerson("Csilla", null)

        val queue = database.syncOperationDao()
        assertEquals(listOf(alicesPerson.id), queue.getPending(alice.id, 10).map { it.entityId })
        assertEquals(listOf(bobsPerson.id), queue.getPending(bob.id, 10).map { it.entityId })
        // The data from before accounts stays where it was, open to nobody.
        assertEquals(legacy, ownerOfPerson(anna))

        session.signOut()
        assertEquals(emptyList<UUID>(), people.observePeople().first().map { it.id })
        assertTrue(runCatching { people.addPerson("Dóra", null) }.exceptionOrNull() is NoActiveOwnerException)
    }

    // ---- data ----

    /**
     * Data from before accounts existed, which is no longer handed over: a person of the old
     * device id and its pending create, migrated without an owner.
     */
    private suspend fun seedLegacyData(): UUID {
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
