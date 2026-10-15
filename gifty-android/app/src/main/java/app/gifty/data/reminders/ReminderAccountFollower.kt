package app.gifty.data.reminders

import app.gifty.data.user.ActiveOwner
import app.gifty.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps reminders in step with whose data is open. Each change of account first removes the
 * digests of everyone else, so one account's names never stay in view for another; an open
 * account then gets its heartbeat if it chose reminders, including after a crash that came
 * between saving that choice and scheduling it.
 */
@Singleton
class ReminderAccountFollower @Inject constructor(
    private val activeOwner: ActiveOwner,
    private val preferences: ReminderPreferences,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private var started = false

    /** Called once from the Application; later calls do nothing. */
    fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }
        scope.launch {
            activeOwner.ownerId.collect { owner ->
                notifier.cancelAllExcept(owner)
                // No owner happens on every restore and switch too; only an explicit sign-out cancels.
                if (owner == null) return@collect
                if (preferences.isEnabled(owner)) scheduler.ensureScheduled() else scheduler.cancel()
            }
        }
    }
}
