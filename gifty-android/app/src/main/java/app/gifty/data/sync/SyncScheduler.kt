package app.gifty.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Background sync jobs: they only ask [ForegroundSyncCoordinator] for drains, never sync themselves. */
interface SyncScheduler {

    /**
     * Asks for one background push of local changes once a network is available, unless one is
     * already waiting. Called after local changes are committed; it is a request to WorkManager,
     * kept across process death only once WorkManager has stored it.
     */
    fun requestCatchUp()

    /** Keeps the periodic full sync scheduled; an existing schedule is left as it is. */
    fun schedulePeriodic()

    /** Drops both jobs, e.g. on signing out. */
    fun cancelAll()
}

class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    override fun requestCatchUp() {
        val request = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            .setConstraints(CONNECTED)
            // Usually the foreground drain has pushed the change by then, so the job finds little to do.
            .setInitialDelay(CATCH_UP_DELAY_MINUTES, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(BackgroundSyncWorker.KEY_FULL to false))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(CATCH_UP, ExistingWorkPolicy.KEEP, request)
    }

    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(CONNECTED)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(BackgroundSyncWorker.KEY_FULL to true))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(CATCH_UP)
            cancelUniqueWork(PERIODIC)
        }
    }

    companion object {
        const val CATCH_UP = "gifty-sync-catchup"
        const val PERIODIC = "gifty-sync-periodic"
        const val CATCH_UP_DELAY_MINUTES = 1L
        const val PERIOD_HOURS = 12L
        const val BACKOFF_SECONDS = 30L

        private val CONNECTED = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
