package app.giftiary.data.user

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
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.testing.ACCOUNT_TABLES
import app.giftiary.testing.FakeAuthRepository
import app.giftiary.testing.FakeDeviceProofRemote
import app.giftiary.testing.FakeRecoveryGate
import app.giftiary.testing.FakeReminderScheduler
import app.giftiary.testing.FakeSyncScheduler
import app.giftiary.testing.rowsOf
import app.giftiary.testing.seedAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import java.time.LocalDate
import java.util.UUID

/**
 * A device holding data of an account whose session is gone removes that data only once the server
 * confirms the account no longer exists, never on a lost session or a failed question alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeletedAccountCheckTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alice = AuthUser(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), "alice@example.com")
    private val bob = AuthUser(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"), "bob@example.com")

    private val auth = FakeAuthRepository()
    private val server = FakeDeviceProofRemote()
    private val syncScheduler = FakeSyncScheduler()
    private val notifier = ReminderNotifier(context, DeviceNotificationAccess(context))
    private val reminderPreferences = ReminderPreferences(context)
    private val marker = AccountDeletionMarker(context)
    private val proofs = DeviceProofs(context)
    private val photoFiles by lazy { GiftPhotoFiles(context) }
    private val recoveryGate = FakeRecoveryGate()
    private lateinit var database: GiftiaryDatabase

    private val everyTable = ACCOUNT_TABLES.associateWith { 1 }
    private val noRows = ACCOUNT_TABLES.associateWith { 0 }
    private val alicesPhoto by lazy { photoFiles.newPath(alice.id) }
    private val bobsPhoto by lazy { photoFiles.newPath(bob.id) }

    private var process: Job? = null

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, GiftiaryDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** A fresh process, started as the Application does; the previous one is ended first. */
    private fun TestScope.startProcess(): Pair<OwnerSession, DeletedAccountCheck> {
        process?.cancel()
        val job = SupervisorJob(backgroundScope.coroutineContext[Job])
        process = job
        val scope = CoroutineScope(backgroundScope.coroutineContext + job + UnconfinedTestDispatcher(testScheduler))
        val reminderSignOut = ReminderSignOut(FakeReminderScheduler(), notifier)
        val session = OwnerSession(auth, syncScheduler, reminderSignOut, recoveryGate, marker, scope)
        val purge = LocalAccountPurge(
            auth, database.accountDataDao(), syncScheduler, reminderSignOut, reminderPreferences, notifier, photoFiles, proofs,
        )
        val check = DeletedAccountCheck(session, auth, server, proofs, marker, purge, scope)
        session.start()
        check.start()
        return session to check
    }

    private suspend fun seedAliceAndBob() {
        database.seedAccount(alice.id)
        database.seedAccount(bob.id)
        photoFiles.write(alicesPhoto, byteArrayOf(1))
        photoFiles.write(bobsPhoto, byteArrayOf(2))
        reminderPreferences.setEnabled(alice.id, true)
        reminderPreferences.setLastDigest(alice.id, LocalDate.of(2026, 10, 20))
        reminderPreferences.setEnabled(bob.id, true)
    }

    private fun assertAliceKept() {
        assertEquals(everyTable, database.rowsOf(alice.id))
        assertTrue(photoFiles.exists(alicesPhoto))
        assertTrue(reminderPreferences.isEnabled(alice.id))
    }

    // The purge runs on Room's own threads, so its end is awaited in real time.
    private suspend fun assertAliceGone() {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (proofs.secret(alice.id) != null || database.rowsOf(alice.id) != noRows) delay(10)
            }
        }
        assertEquals(noRows, database.rowsOf(alice.id))
        assertFalse(photoFiles.exists(alicesPhoto))
        assertFalse(reminderPreferences.isEnabled(alice.id))
        assertNull("her proof is forgotten too", proofs.secret(alice.id))
        assertFalse(alice.id in proofs.unverified())
    }

    private fun assertBobKept() {
        assertEquals(everyTable, database.rowsOf(bob.id))
        assertTrue(photoFiles.exists(bobsPhoto))
        assertTrue(reminderPreferences.isEnabled(bob.id))
    }

    /** Alice was signed in on this device long enough to register its proof. */
    private fun TestScope.aliceWasSignedIn(): Pair<OwnerSession, DeletedAccountCheck> {
        auth.state.value = AuthState.SignedIn(alice)
        val started = startProcess()
        assertNotNull("the device registered its proof while signed in", proofs.secret(alice.id))
        return started
    }

    // ---- the server confirms ----

    @Test
    fun `an account deleted on another device is removed here once the server confirms it`() = runTest {
        seedAliceAndBob()
        val (session, _) = aliceWasSignedIn()

        server.delete(alice.id)
        // The SDK drops the session: the refresh token was rejected.
        auth.state.value = AuthState.SignedOut

        assertAliceGone()
        assertBobKept()
        assertEquals(OwnerSessionState.SignedOut, session.state.value)
        assertEquals("nothing but stopping is asked of sync", listOf(FakeSyncScheduler.CANCEL), syncScheduler.calls)
    }

    @Test
    fun `a live account whose session was dropped keeps everything`() = runTest {
        seedAliceAndBob()
        aliceWasSignedIn()

        // Revoked elsewhere, or a 429 the SDK took as a rejection: the account itself exists.
        auth.state.value = AuthState.SignedOut

        assertEquals("the server was asked", 1, server.questions.size)
        assertAliceKept()
        assertTrue("and still is unverified, so a later deletion is seen", alice.id in proofs.unverified())
        assertEquals(0, auth.clearLocalSessionCalls)
    }

    @Test
    fun `no answer is never taken as one`() = runTest {
        seedAliceAndBob()
        val (_, check) = aliceWasSignedIn()
        server.delete(alice.id)

        for ((index, failure) in RemoteFailure.entries.withIndex()) {
            server.questionFailure = failure
            if (index == 0) auth.state.value = AuthState.SignedOut else check.requestCheck()
            assertAliceKept()
        }
        assertEquals(RemoteFailure.entries.size, server.questions.size)

        server.questionFailure = null
        check.requestCheck()
        assertAliceGone()
    }

    @Test
    fun `without a registered proof nothing is asked and nothing removed`() = runTest {
        seedAliceAndBob()
        server.registerFailure = RemoteFailure.NETWORK
        auth.state.value = AuthState.SignedIn(alice)
        val (_, check) = startProcess()
        assertNull("a secret is kept only once the server has its hash", proofs.secret(alice.id))

        server.delete(alice.id)
        auth.state.value = AuthState.SignedOut
        check.requestCheck()

        assertTrue(server.questions.isEmpty())
        assertAliceKept()
    }

    @Test
    fun `a failed registration is tried again at the next start`() = runTest {
        server.registerFailure = RemoteFailure.SERVER
        auth.state.value = AuthState.SignedIn(alice)
        val (_, check) = startProcess()
        assertNull(proofs.secret(alice.id))

        server.registerFailure = null
        check.requestCheck()

        assertNotNull(proofs.secret(alice.id))
        check.requestCheck()
        assertEquals("once registered, never again", 2, server.registrations.size)
    }

    // ---- restarts ----

    @Test
    fun `a restart with the check pending asks again, and removes only on a confirmation`() = runTest {
        seedAliceAndBob()
        aliceWasSignedIn()
        server.questionFailure = RemoteFailure.NETWORK
        auth.state.value = AuthState.SignedOut
        assertAliceKept()

        // A new process with no session at all: what is on disk says Alice is still to be checked.
        startProcess()
        assertAliceKept()

        server.questionFailure = null
        startProcess()
        assertAliceKept()

        server.delete(alice.id)
        startProcess()
        assertAliceGone()
    }

    @Test
    fun `a session that ended while the app was not running is noticed at the next start`() = runTest {
        seedAliceAndBob()
        aliceWasSignedIn()
        process?.cancel()

        server.delete(alice.id)
        auth.state.value = AuthState.SignedOut
        startProcess()

        assertAliceGone()
    }

    @Test
    fun `removing an account a second time is harmless`() = runTest {
        seedAliceAndBob()
        aliceWasSignedIn()
        server.delete(alice.id)
        auth.state.value = AuthState.SignedOut
        assertAliceGone()

        proofs.markUnverified(alice.id)
        startProcess()
        LocalAccountPurge(
            auth, database.accountDataDao(), syncScheduler, ReminderSignOut(FakeReminderScheduler(), notifier), reminderPreferences,
            notifier, photoFiles, proofs,
        ).purge(alice.id)

        assertAliceGone()
        assertBobKept()
    }

    // ---- accounts that must never be touched ----

    @Test
    fun `the same account signing in again is verified and keeps its data`() = runTest {
        seedAliceAndBob()
        val (session, check) = aliceWasSignedIn()
        server.questionFailure = RemoteFailure.NETWORK
        auth.state.value = AuthState.SignedOut
        assertTrue(alice.id in proofs.unverified())

        server.questionFailure = null
        auth.state.value = AuthState.SignedIn(alice)
        check.requestCheck()

        assertFalse(alice.id in proofs.unverified())
        assertEquals("nothing more is asked about an account that is signed in", 1, server.questions.size)
        assertEquals(OwnerSessionState.Ready(alice), session.state.value)
        assertAliceKept()
    }

    @Test
    fun `another account signed in meanwhile keeps its data, session and sync`() = runTest {
        seedAliceAndBob()
        val (session, check) = aliceWasSignedIn()
        server.questionFailure = RemoteFailure.NETWORK
        auth.state.value = AuthState.SignedOut
        auth.state.value = AuthState.SignedIn(bob)
        assertNotNull(proofs.secret(bob.id))
        syncScheduler.calls.clear()

        server.questionFailure = null
        server.delete(alice.id)
        check.requestCheck()

        assertAliceGone()
        assertBobKept()
        assertEquals(OwnerSessionState.Ready(bob), session.state.value)
        assertEquals(bob.id, session.ownerId.value)
        assertEquals(0, auth.clearLocalSessionCalls)
        assertTrue("Bob's sync is left alone", syncScheduler.calls.isEmpty())
        assertNotNull(proofs.secret(bob.id))
        assertFalse(bob.id in proofs.unverified())
    }

    @Test
    fun `the open account is never asked about, whatever the server would say`() = runTest {
        seedAliceAndBob()
        val (_, check) = aliceWasSignedIn()
        proofs.markUnverified(alice.id)
        server.delete(alice.id)

        check.requestCheck()

        assertTrue(server.questions.isEmpty())
        assertAliceKept()
    }

    @Test
    fun `an account signed in but held closed is not removed`() = runTest {
        seedAliceAndBob()
        aliceWasSignedIn()
        process?.cancel()
        // A password recovery keeps her data closed although her session is in place.
        recoveryGate.isActive.value = true
        proofs.markUnverified(alice.id)
        server.delete(alice.id)

        val (session, check) = startProcess()
        check.requestCheck()

        assertEquals(OwnerSessionState.Restoring, session.state.value)
        assertTrue(server.questions.isEmpty())
        assertAliceKept()
    }

    @Test
    fun `an account this device is deleting itself is left to its own deletion`() = runTest {
        seedAliceAndBob()
        aliceWasSignedIn()
        marker.set(alice.id)
        server.delete(alice.id)
        auth.state.value = AuthState.SignedOut

        assertTrue(server.questions.isEmpty())
        assertAliceKept()
        marker.clear()
    }
}
