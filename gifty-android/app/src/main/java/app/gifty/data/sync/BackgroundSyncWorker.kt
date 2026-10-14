package app.gifty.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Runs one [BackgroundSync] job for [SyncScheduler]. Created by WorkManager's default factory, so
 * it reaches the app's objects through an entry point rather than by injection.
 */
class BackgroundSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).backgroundSync()
        val again = sync.run(full = inputData.getBoolean(KEY_FULL, false), attempt = runAttemptCount)
        return if (again) Result.retry() else Result.success()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun backgroundSync(): BackgroundSync
    }

    companion object {
        /** Whether the job also pulls the backend's data, not only pushes local changes. */
        const val KEY_FULL = "full"
    }
}
