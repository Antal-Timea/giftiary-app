package app.gifty.data.reminders

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.UpcomingOccasion
import app.gifty.domain.repository.OccasionDetails
import app.gifty.testing.FakeActiveOwner
import app.gifty.testing.FakeReminderScheduler
import app.gifty.testing.occasion
import kotlinx.coroutines.CoroutineScope
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
        val scheduler = FakeReminderScheduler()
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

    @Test
    fun `when the account changes, other accounts' digests go and the heartbeat follows the new account's choice`() = runTest {
        val owner = FakeActiveOwner(alice)
        val scheduler = FakeReminderScheduler()
        preferences.setEnabled(alice, true)
        notifier.post(alice, listOf(due()))
        val follower = ReminderAccountFollower(owner, preferences, scheduler, notifier, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

        follower.start()
        assertEquals("open with reminders on", listOf(FakeReminderScheduler.ENSURE), scheduler.calls)
        assertEquals(listOf(alice.toString()), manager.activeNotifications.map { it.tag })

        owner.ownerId.value = null
        assertTrue("no account, no names", manager.activeNotifications.isEmpty())
        assertEquals("closing is not signing out: the heartbeat stays", 1, scheduler.calls.size)

        notifier.post(alice, listOf(due()))
        owner.ownerId.value = bob
        assertTrue(manager.activeNotifications.isEmpty())
        assertEquals("bob has reminders off", FakeReminderScheduler.CANCEL, scheduler.calls.last())
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
