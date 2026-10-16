package app.giftiary.data.reminders

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The reminder heartbeat: a periodic job that checks whether a digest is due. WorkManager keeps it
 * across process death and reboots, so no job ever has to schedule the next one itself.
 */
interface ReminderScheduler {

    /** Keeps the heartbeat scheduled; an existing schedule is left as it is. */
    fun ensureScheduled()

    fun cancel()
}

class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    override fun ensureScheduled() {
        // No constraints: reminders only read this device's data.
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    companion object {
        const val NAME = "giftiary-reminders"

        /**
         * Each run in a stopped process starts Giftiary, which also syncs; every 4 hours keeps that
         * to a few times a day while still reaching most users in the morning.
         */
        const val PERIOD_HOURS = 4L
        const val BACKOFF_SECONDS = 30L
    }
}
