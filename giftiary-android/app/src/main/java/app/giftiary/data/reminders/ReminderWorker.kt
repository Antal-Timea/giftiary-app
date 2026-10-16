package app.giftiary.data.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Runs one [ReminderDigest] heartbeat for [ReminderScheduler]. Created by WorkManager's default
 * factory, so it reaches the app's objects through an entry point rather than by injection.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val digest = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).reminderDigest()
        return if (digest.run(attempt = runAttemptCount)) Result.retry() else Result.success()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun reminderDigest(): ReminderDigest
    }
}
