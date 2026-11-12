package app.giftiary.data.user

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.photos.GiftPhotoFiles
import app.giftiary.data.reminders.DeviceNotificationAccess
import app.giftiary.data.reminders.ReminderNotifier
import app.giftiary.data.reminders.ReminderPreferences
import app.giftiary.data.reminders.ReminderSignOut
import app.giftiary.data.remote.RemoteFailure
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.UpcomingOccasion
import app.giftiary.domain.repository.AccountDeletionException
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.domain.repository.AccountDeletionState
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.testing.ACCOUNT_TABLES
import app.giftiary.testing.FakeAccountDeletionRemote
import app.giftiary.testing.FakeAuthRepository
import app.giftiary.testing.FakeRecoveryGate
import app.giftiary.testing.FakeReminderScheduler
import app.giftiary.testing.FakeSyncScheduler
import app.giftiary.testing.occasion
import app.giftiary.testing.rowsOf
import app.giftiary.testing.seedAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountDeleterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alice = AuthUser(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), "alice@example.com")
    private val bob = AuthUser(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"), "bob@example.com")

    private val auth = FakeAuthRepository()
    private val remote = FakeAccountDeletionRemote()
    private val syncScheduler = FakeSyncScheduler()
    private val reminderScheduler = FakeReminderScheduler()
    private val notifier = ReminderNotifier(context, DeviceNotificationAccess(context))
    private val reminderPreferences = ReminderPreferences(context)
    private val marker = AccountDeletionMarker(context)
    private val deviceProofs = DeviceProofs(context)
    private lateinit var database: GiftiaryDatabase

    private val everyTable = ACCOUNT_TABLES.associateWith { 1 }
    private val noRows = ACCOUNT_TABLES.associateWith { 0 }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, GiftiaryDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** A fresh process: the session and the deleter, built and started as the Application does. */
    private fun TestScope.startProcess(): Pair<OwnerSession, AccountDeleter> {
        // Supervised, as the application's scope is: a failed deletion fails its caller, not the scope.
        val scope = CoroutineScope(
            backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]) + UnconfinedTestDispatcher(testScheduler),
        )
        val reminderSignOut = ReminderSignOut(reminderScheduler, notifier)
        val session = OwnerSession(auth, syncScheduler, reminderSignOut, FakeRecoveryGate(), marker, scope)
        val purge = LocalAccountPurge(
            auth, database.accountDataDao(), syncScheduler, reminderSignOut, reminderPreferences, notifier, photoFiles, deviceProofs,
        )
        val deleter = AccountDeleter(session, auth, remote, marker, purge, scope)
        session.start()
        deleter.start()
        return session to deleter
    }

    private val photoFiles by lazy { GiftPhotoFiles(context) }
    private val alicesPhoto by lazy { photoFiles.newPath(alice.id) }
    private val bobsPhoto by lazy { photoFiles.newPath(bob.id) }

    private suspend fun seedAliceAndBob() {
        database.seedAccount(alice.id)
        database.seedAccount(bob.id)
        photoFiles.write(alicesPhoto, byteArrayOf(1))
        photoFiles.write(bobsPhoto, byteArrayOf(2))
        reminderPreferences.setEnabled(alice.id, true)
        reminderPreferences.setLastDigest(alice.id, LocalDate.of(2026, 10, 20))
        reminderPreferences.setEnabled(bob.id, true)
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.post(alice.id, listOf(digestEntry()))
    }

    // ---- deleting from the app ----

    @Test
    fun `the server is asked first, while the account and its session are still in place`() = runTest {
        seedAliceAndBob()
        auth.state.value = AuthState.SignedIn(alice)
        val (session, deleter) = startProcess()
        remote.onCall = { owner ->
            assertEquals(alice.id, owner)
            assertEquals("the marker is on disk before the request leaves", alice.id, marker.owner())
            assertEquals(alice.id, session.ownerId.value)
            assertEquals(everyTable, database.rowsOf(alice.id))
            assertEquals(0, auth.clearLocalSessionCalls)
            assertTrue(syncScheduler.calls.isEmpty())
        }

        deleter.deleteAccount()

        assertEquals(listOf(alice.id), remote.calls)
    }

    @Test
    fun `after the server deleted it, the account is closed and removed from this device`() = runTest {
        seedAliceAndBob()
        auth.state.value = AuthState.SignedIn(alice)
        val (session, deleter) = startProcess()
        auth.onClearLocalSession = {
            // The session goes after everything else, and the marker only after the session.
            assertNull(session.ownerId.value)
            assertEquals(noRows, database.rowsOf(alice.id))
            assertEquals(alice.id, marker.owner())
        }

        deleter.deleteAccount()

        assertNull(session.ownerId.value)
        assertEquals(OwnerSessionState.SignedOut, session.state.value)
        assertTrue(FakeSyncScheduler.CANCEL in syncScheduler.calls)
        assertTrue(FakeReminderScheduler.CANCEL in reminderScheduler.calls)
        assertTrue("the digest naming Alice's people is gone", activeDigests().isEmpty())
        assertFalse(reminderPreferences.isEnabled(alice.id))
        assertNull(reminderPreferences.lastDigest(alice.id))
        assertEquals(noRows, database.rowsOf(alice.id))
        assertFalse("Alice's photos are gone from this device", photoFiles.exists(alicesPhoto))
        assertEquals(1, auth.clearLocalSessionCalls)
        assertEquals("no network sign-out is needed", 0, auth.signOutCalls)
        assertNull(marker.owner())
        assertEquals(AccountDeletionState.Deleted, deleter.state.value)

        // Another account on this device keeps everything.
        assertEquals(everyTable, database.rowsOf(bob.id))
        assertTrue(reminderPreferences.isEnabled(bob.id))
        assertTrue(photoFiles.exists(bobsPhoto))
    }

    @Test
    fun `the account is closed before anything of it is deleted`() = runTest {
        database.seedAccount(alice.id)
        auth.state.value = AuthState.SignedIn(alice)
        val (session, deleter) = startProcess()
        val owners = mutableListOf<UUID?>()
        remote.onCall = { owners += session.ownerId.value }
        auth.onClearLocalSession = { owners += session.ownerId.value }

        deleter.deleteAccount()

        assertEquals(listOf(alice.id, null), owners)
    }

    @Test
    fun `a refused deletion changes nothing on this device`() = runTest {
        for (failure in listOf(RemoteFailure.NETWORK, RemoteFailure.SERVER, RemoteFailure.UNAUTHENTICATED)) {
            database.seedAccount(alice.id)
            auth.state.value = AuthState.SignedIn(alice)
            val (session, deleter) = startProcess()
            remote.failure = failure

            val thrown = runCatching { deleter.deleteAccount() }.exceptionOrNull()

            val expected = if (failure == RemoteFailure.NETWORK) AccountDeletionFailure.NETWORK else AccountDeletionFailure.SERVER
            assertEquals(expected, (thrown as AccountDeletionException).failure)
            assertNull("$failure: nothing is left to finish", marker.owner())
            assertEquals(alice.id, session.ownerId.value)
            assertEquals(OwnerSessionState.Ready(alice), session.state.value)
            assertEquals(everyTable, database.rowsOf(alice.id))
            assertEquals(0, auth.clearLocalSessionCalls)
            assertTrue(syncScheduler.calls.isEmpty())
            assertEquals(AccountDeletionState.Idle, deleter.state.value)
            database.accountDataDao().purge(alice.id)
        }
    }

    @Test
    fun `asking again while a deletion runs sends no second request`() = runTest {
        database.seedAccount(alice.id)
        auth.state.value = AuthState.SignedIn(alice)
        val (_, deleter) = startProcess()
        val gate = CompletableDeferred<Unit>()
        remote.gate = gate

        val first = async { deleter.deleteAccount() }
        val second = async { deleter.deleteAccount() }
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf(alice.id), remote.calls)
        assertEquals(1, auth.clearLocalSessionCalls)
    }

    @Test
    fun `without an open account nothing is sent`() = runTest {
        auth.state.value = AuthState.SignedOut
        val (_, deleter) = startProcess()

        try {
            deleter.deleteAccount()
            fail("expected a failure")
        } catch (e: AccountDeletionException) {
            assertEquals(AccountDeletionFailure.SERVER, e.failure)
        }
        assertTrue(remote.calls.isEmpty())
        assertNull(marker.owner())
    }

    // ---- finishing after the app stopped ----

    @Test
    fun `a deletion the server may have done is finished on the next start, asking again with the session it still has`() = runTest {
        database.seedAccount(alice.id)
        database.seedAccount(bob.id)
        // The previous process died after setting the marker: the request may or may not have arrived.
        marker.set(alice.id)
        auth.state.value = AuthState.SignedIn(alice)

        val (session, deleter) = startProcess()
        deleter.awaitState(AccountDeletionState.Deleted)

        assertEquals(listOf(alice.id), remote.calls)
        assertEquals(noRows, database.rowsOf(alice.id))
        assertEquals(everyTable, database.rowsOf(bob.id))
        assertEquals(1, auth.clearLocalSessionCalls)
        assertNull(marker.owner())
        assertEquals(OwnerSessionState.SignedOut, session.state.value)
        assertEquals(AccountDeletionState.Deleted, deleter.state.value)
    }

    @Test
    fun `an account whose session is gone is taken as deleted and finished without the server`() = runTest {
        database.seedAccount(alice.id)
        marker.set(alice.id)
        // The SDK dropped the session: the server refused its refresh token, as it does once the user is gone.
        auth.state.value = AuthState.SignedOut

        val (session, deleter) = startProcess()
        deleter.awaitState(AccountDeletionState.Deleted)

        assertTrue(remote.calls.isEmpty())
        assertEquals(noRows, database.rowsOf(alice.id))
        assertNull(marker.owner())
        assertEquals(OwnerSessionState.SignedOut, session.state.value)
        assertEquals(AccountDeletionState.Deleted, deleter.state.value)
    }

    @Test
    fun `a failed request on the next start proves nothing and keeps the account closed`() = runTest {
        for (failure in listOf(RemoteFailure.NETWORK, RemoteFailure.SERVER, RemoteFailure.UNAUTHENTICATED)) {
            database.seedAccount(alice.id)
            marker.set(alice.id)
            auth.state.value = AuthState.SignedIn(alice)
            remote.failure = failure

            val (session, deleter) = startProcess()

            val expected = if (failure == RemoteFailure.NETWORK) AccountDeletionFailure.NETWORK else AccountDeletionFailure.SERVER
            assertEquals(AccountDeletionState.FinishFailed(expected), deleter.state.value)
            assertEquals("$failure: still to finish", alice.id, marker.owner())
            assertEquals(everyTable, database.rowsOf(alice.id))
            assertEquals(0, auth.clearLocalSessionCalls)
            assertNull("never opened while its deletion is unfinished", session.ownerId.value)
            assertEquals(OwnerSessionState.Restoring, session.state.value)

            // A retry once the server answers finishes it.
            remote.failure = null
            deleter.retry()
            deleter.awaitState(AccountDeletionState.Deleted)
            assertEquals(noRows, database.rowsOf(alice.id))
            assertNull(marker.owner())
            auth.clearLocalSessionCalls = 0
            remote.calls.clear()
        }
    }

    @Test
    fun `losing the session while a failed finish waits completes it`() = runTest {
        database.seedAccount(alice.id)
        marker.set(alice.id)
        auth.state.value = AuthState.SignedIn(alice)
        remote.failure = RemoteFailure.UNAUTHENTICATED
        val (session, deleter) = startProcess()
        assertEquals(AccountDeletionState.FinishFailed(AccountDeletionFailure.SERVER), deleter.state.value)

        auth.state.value = AuthState.SignedOut
        deleter.awaitState(AccountDeletionState.Deleted)

        assertEquals(noRows, database.rowsOf(alice.id))
        assertEquals(OwnerSessionState.SignedOut, session.state.value)
    }

    @Test
    fun `another account signed in meanwhile keeps its session, sync and reminders`() = runTest {
        database.seedAccount(alice.id)
        database.seedAccount(bob.id)
        marker.set(alice.id)
        auth.state.value = AuthState.SignedIn(bob)

        val (session, deleter) = startProcess()
        deleter.awaitState(AccountDeletionState.Deleted)

        assertTrue("Alice's session is gone, so nothing can be asked as her", remote.calls.isEmpty())
        assertEquals(noRows, database.rowsOf(alice.id))
        assertEquals(everyTable, database.rowsOf(bob.id))
        assertEquals(bob.id, session.ownerId.value)
        assertEquals("Bob stays signed in", 0, auth.clearLocalSessionCalls)
        assertFalse("and keeps his sync", FakeSyncScheduler.CANCEL in syncScheduler.calls)
        assertFalse("and his reminders", FakeReminderScheduler.CANCEL in reminderScheduler.calls)
    }

    @Test
    fun `the message is shown once`() = runTest {
        auth.state.value = AuthState.SignedIn(alice)
        val (_, deleter) = startProcess()
        deleter.deleteAccount()

        deleter.onDeletedShown()

        assertEquals(AccountDeletionState.Idle, deleter.state.value)
    }

    // Room runs the purge on its own thread, so finishing on start is awaited in real time.
    private suspend fun AccountDeleter.awaitState(expected: AccountDeletionState) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { state.first { it == expected } } }

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
