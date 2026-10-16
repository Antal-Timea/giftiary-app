package app.giftiary.data.reminders

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.UpcomingOccasion
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.FakeReminderScheduler
import app.giftiary.testing.occasion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

/** The heartbeat as WorkManager records it, the per-account choice, and how both follow the open account. */
@RunWith(RobolectricTestRunner::class)
class ReminderSchedulingTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = application.getSystemService(NotificationManager::class.java)
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val preferences = ReminderPreferences(application)
    private val notifier = ReminderNotifier(application, DeviceNotificationAccess(application))
    private val scheduler = FakeReminderScheduler()
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        workManager = WorkManager.getInstance(application)
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `the heartbeat is one job every four hours, needing no network, kept across schedules`() {
        val scheduler = WorkManagerReminderScheduler(application)
        scheduler.ensureScheduled()
        val first = heartbeat().single()
        scheduler.ensureScheduled()

        val job = heartbeat().single()
        assertEquals("kept, not replaced", first.id, job.id)
        assertEquals(first.generation, job.generation)
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        assertEquals(TimeUnit.HOURS.toMillis(4), job.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(NetworkType.NOT_REQUIRED, job.constraints.requiredNetworkType)
        assertTrue(job.tags.contains(ReminderWorker::class.java.name))

        scheduler.cancel()
        assertEquals(WorkInfo.State.CANCELLED, heartbeat().single().state)
    }

    @Test
    fun `each account keeps its own choice and day, also across signing out and back in`() {
        preferences.setEnabled(alice, true)
        preferences.setLastDigest(alice, LocalDate.of(2026, 5, 10))

        assertFalse("bob inherits nothing", preferences.isEnabled(bob))
        assertNull(preferences.lastDigest(bob))
        // Nothing clears them on signing out; a new process reads them back.
        val later = ReminderPreferences(application)
        assertTrue(later.isEnabled(alice))
        assertEquals(LocalDate.of(2026, 5, 10), later.lastDigest(alice))
    }

    @Test
    fun `turning reminders on and off is the open account's, and runs or stops the heartbeat`() {
        val owner = FakeActiveOwner(alice)
        val settings = ReminderSettingsRepositoryImpl(owner, preferences, scheduler, notifier, DeviceNotificationAccess(application))
        notifier.post(alice, listOf(due()))

        settings.setEnabled(true)
        assertEquals(true, settings.isEnabled())
        assertTrue(preferences.isEnabled(alice))
        assertTrue("the channel exists before any digest", manager.getNotificationChannel(ReminderNotifier.CHANNEL_ID) != null)
        settings.setEnabled(false)

        assertEquals(listOf(FakeReminderScheduler.ENSURE, FakeReminderScheduler.CANCEL), scheduler.calls)
        assertTrue("turning off takes back today's digest", manager.activeNotifications.isEmpty())
        owner.ownerId.value = null
        assertNull(settings.isEnabled())
        settings.setEnabled(true)
        assertFalse(preferences.isEnabled(bob))
        assertEquals(2, scheduler.calls.size)
    }

    // How digests follow the account. A digest is shown by its tag; "other" is an unrelated notification.

    @Test
    fun `a process starting while the account is restored leaves the account's digest alone`() = runTest {
        notifier.post(alice, listOf(due()))
        postUnrelated()
        val session = FakeAccountSession(OwnerSessionState.Restoring)

        follow(session)

        assertEquals(setOf(alice.toString(), "other"), shownTags())
        assertTrue("nothing decided while restoring", scheduler.calls.isEmpty())
    }

    @Test
    fun `the restored account keeps its digest and its heartbeat`() = runTest {
        preferences.setEnabled(alice, true)
        notifier.post(alice, listOf(due()))
        val session = FakeAccountSession(OwnerSessionState.Restoring)
        follow(session)

        session.state.value = ready(alice)

        assertEquals(setOf(alice.toString()), shownTags())
        assertEquals(listOf(FakeReminderScheduler.ENSURE), scheduler.calls)
    }

    @Test
    fun `signing out takes the digest back and stops the heartbeat at once, keeping the account's choice`() {
        preferences.setEnabled(alice, true)
        preferences.setLastDigest(alice, LocalDate.of(2026, 5, 10))
        notifier.post(alice, listOf(due()))
        postUnrelated()

        ReminderSignOut(scheduler, notifier).onSignOut()

        assertEquals(setOf("other"), shownTags())
        assertEquals(listOf(FakeReminderScheduler.CANCEL), scheduler.calls)
        assertTrue(preferences.isEnabled(alice))
        assertEquals(LocalDate.of(2026, 5, 10), preferences.lastDigest(alice))
    }

    @Test
    fun `another account opening takes the first one's digest away, and keeps its own`() = runTest {
        preferences.setEnabled(alice, true)
        notifier.post(alice, listOf(due()))
        postUnrelated()
        val session = FakeAccountSession(ready(alice))
        follow(session)

        session.state.value = OwnerSessionState.Restoring
        assertEquals("not yet: it may be alice again", setOf(alice.toString(), "other"), shownTags())
        notifier.post(bob, listOf(due()))
        session.state.value = ready(bob)

        assertEquals(setOf(bob.toString(), "other"), shownTags())
        assertEquals("bob has reminders off", FakeReminderScheduler.CANCEL, scheduler.calls.last())
    }

    @Test
    fun `signed out, no account's digest is left, other notifications stay`() = runTest {
        notifier.post(alice, listOf(due()))
        notifier.post(bob, listOf(due()))
        postUnrelated()
        val session = FakeAccountSession(OwnerSessionState.Restoring)
        follow(session)

        session.state.value = OwnerSessionState.SignedOut

        assertEquals(setOf("other"), shownTags())
    }

    @Test
    fun `an account that failed to open keeps only its own digest`() = runTest {
        notifier.post(alice, listOf(due()))
        notifier.post(bob, listOf(due()))
        val session = FakeAccountSession(OwnerSessionState.Restoring)
        follow(session)

        session.state.value = OwnerSessionState.Failed(AuthUser(bob, null))

        assertEquals(setOf(bob.toString()), shownTags())
    }

    private fun TestScope.follow(session: AccountSession) {
        ReminderAccountFollower(session, preferences, scheduler, notifier, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
            .start()
    }

    private fun shownTags() = manager.activeNotifications.map { it.tag ?: "other" }.toSet()

    private fun postUnrelated() {
        manager.createNotificationChannel(NotificationChannel("other", "Other", NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(ReminderNotifier.DIGEST_ID, NotificationCompat.Builder(application, "other").setSmallIcon(android.R.drawable.ic_dialog_info).build())
    }

    private fun ready(owner: UUID) = OwnerSessionState.Ready(AuthUser(owner, null))

    private class FakeAccountSession(initial: OwnerSessionState) : AccountSession {
        override val state = MutableStateFlow(initial)
        override fun retry() = Unit
        override fun signOut() = Unit
    }

    private fun heartbeat() = workManager.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.NAME).get()

    private fun due() = UpcomingOccasion(
        occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true)),
        null,
        LocalDate.of(2026, 12, 24),
        0,
        null,
    )
}
