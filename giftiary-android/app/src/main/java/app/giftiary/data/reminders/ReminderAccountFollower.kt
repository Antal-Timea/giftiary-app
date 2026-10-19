package app.giftiary.data.reminders

import app.giftiary.di.ApplicationScope
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps reminders in step with whose data is open. An open account keeps only its own digest, so
 * another account's names never stay in view, and gets its heartbeat if it chose reminders,
 * including after a crash that came between saving that choice and scheduling it.
 *
 * Restoring decides nothing: every process start passes through it, also one started for other
 * background work, and the account being restored is usually the one whose digest is showing.
 * Explicit sign-out is handled at once by [ReminderSignOut].
 */
@Singleton
class ReminderAccountFollower @Inject constructor(
    private val accountSession: AccountSession,
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
            accountSession.state.collect { state ->
                when (state) {
                    OwnerSessionState.Restoring -> Unit
                    OwnerSessionState.SignedOut -> notifier.cancelAllExcept(null)
                    is OwnerSessionState.Ready -> {
                        val owner = state.user.id
                        notifier.cancelAllExcept(owner)
                        if (preferences.isEnabled(owner)) scheduler.ensureScheduled() else scheduler.cancel()
                    }
                }
            }
        }
    }
}
