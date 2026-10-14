package app.gifty.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/** The background jobs as WorkManager records them: names, policies, constraints and timing. */
@RunWith(RobolectricTestRunner::class)
class WorkManagerSyncSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerSyncScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerSyncScheduler(context)
    }

    @Test
    fun `catch-up is one pending push once online, a minute after the change, and further changes keep it`() {
        scheduler.requestCatchUp()
        val first = catchUp().single()
        scheduler.requestCatchUp()
        scheduler.requestCatchUp()

        val job = catchUp().single()
        assertEquals("kept, not replaced", first.id, job.id)
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        assertEquals(NetworkType.CONNECTED, job.constraints.requiredNetworkType)
        assertEquals(TimeUnit.MINUTES.toMillis(1), job.initialDelayMillis)
        assertTrue(job.tags.contains(BackgroundSyncWorker::class.java.name))
        assertNull(job.periodicityInfo)
    }

    @Test
    fun `the periodic job is one full sync every twelve hours once online, kept across schedules`() {
        scheduler.schedulePeriodic()
        val first = periodic().single()
        scheduler.schedulePeriodic()

        val job = periodic().single()
        assertEquals(first.id, job.id)
        assertEquals("kept as scheduled, not updated", first.generation, job.generation)
        assertEquals(NetworkType.CONNECTED, job.constraints.requiredNetworkType)
        assertEquals(TimeUnit.HOURS.toMillis(12), job.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `catch-up only pushes, the periodic job also pulls`() {
        scheduler.requestCatchUp()
        scheduler.schedulePeriodic()

        assertFalse(workSpecFull(WorkManagerSyncScheduler.CATCH_UP))
        assertTrue(workSpecFull(WorkManagerSyncScheduler.PERIODIC))
    }

    @Test
    fun `cancelling drops both jobs, and they can be scheduled again`() {
        scheduler.requestCatchUp()
        scheduler.schedulePeriodic()

        scheduler.cancelAll()

        assertEquals(WorkInfo.State.CANCELLED, catchUp().single().state)
        assertEquals(WorkInfo.State.CANCELLED, periodic().single().state)
        scheduler.schedulePeriodic()
        assertTrue(periodic().any { it.state == WorkInfo.State.ENQUEUED })
    }

    private fun catchUp() = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.CATCH_UP).get()

    private fun periodic() = workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.PERIODIC).get()

    /** Input data is not part of WorkInfo; only WorkManager's own work specs keep it. */
    @Suppress("RestrictedApi")
    private fun workSpecFull(uniqueName: String): Boolean {
        val impl = androidx.work.impl.WorkManagerImpl.getInstance(context)
        val id = workManager.getWorkInfosForUniqueWork(uniqueName).get().single().id
        return impl.workDatabase.workSpecDao().getWorkSpec(id.toString())!!.input.getBoolean(BackgroundSyncWorker.KEY_FULL, false)
    }
}
